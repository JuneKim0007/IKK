# IKK — one machine, two surfaces, one Kotlin backend.
#
#   make up        start backend + frontend
#   make down      stop both, and any running emulator
#   make test      every suite: Kotlin, Node codegen, Web, backend
#   make demo      backend + frontend + a cold emulator, ready to present
#
# Ports: frontend 5173, backend 8000. Override with FRONTEND_PORT / BACKEND_PORT.

SHELL        := /bin/bash
FRONTEND_PORT ?= 5173
BACKEND_PORT  ?= 8000
EDITOR        := apps/web/index.html
EDITOR_URL     = http://localhost:$(FRONTEND_PORT)/$(EDITOR)?api=http://127.0.0.1:$(BACKEND_PORT)
RUN           := .run

.DEFAULT_GOAL := help
.PHONY: help up down frontend backend stop-frontend stop-backend status logs \
        test test-kotlin test-codegen test-web test-backend e2e generate check clean \
        docker-build docker-run android-sync android-install \
        emulator emulator-kill emulator-reset demo

## ---------------------------------------------------------------- help

help:
	@echo ""
	@echo "  IKK"
	@echo ""
	@echo "  make up             backend + frontend, backgrounded"
	@echo "  make down           stop both"
	@echo "  make status         what is running"
	@echo "  make logs           tail both logs"
	@echo ""
	@echo "  make frontend       editor on :$(FRONTEND_PORT)"
	@echo "  make backend        API on :$(BACKEND_PORT)"
	@echo ""
	@echo "  make test           Kotlin + codegen + web + backend"
	@echo "  make e2e            up, PUT a contract, generate, assert artifacts"
	@echo "  make generate       run codegen over the example contract"
	@echo "  make check          codegen determinism (regenerate must be a no-op)"
	@echo ""
	@echo "  make docker-build   build the backend image"
	@echo "  make docker-run     run it on :$(BACKEND_PORT)"
	@echo "  make clean          stop everything and remove build output/logs"
	@echo ""

## ---------------------------------------------------------------- run

up: backend frontend
	@echo ""
	@echo "  editor   $(EDITOR_URL)"
	@echo "  backend  http://localhost:$(BACKEND_PORT)/healthz"
	@echo ""
	@echo "  make down   to stop"

$(RUN):
	@mkdir -p $(RUN)

# Served from the repo ROOT, not apps/web, so the editor can fetch
# docs/fixtures/ from the same origin. Native ES modules, no bundler, no install.
frontend: $(RUN) stop-frontend
	@echo "→ frontend on :$(FRONTEND_PORT)"
	@python3 tools/devserver.py $(FRONTEND_PORT) 127.0.0.1 \
		> $(RUN)/frontend.log 2>&1 & echo $$! > $(RUN)/frontend.pid
	@for i in $$(seq 1 40); do \
		curl -sf -o /dev/null http://127.0.0.1:$(FRONTEND_PORT)/$(EDITOR) && break || sleep 0.25; \
	done
	@curl -sf -o /dev/null http://127.0.0.1:$(FRONTEND_PORT)/$(EDITOR) \
		&& echo "  editor  $(EDITOR_URL)" \
		|| { echo "  FAILED — see $(RUN)/frontend.log"; exit 1; }
	@-command -v open >/dev/null && open "$(EDITOR_URL)" >/dev/null 2>&1 || true

backend: $(RUN) stop-backend
	@echo "→ backend on :$(BACKEND_PORT)"
	@./gradlew :apps:backend:bootRun --args='--server.port=$(BACKEND_PORT)' \
		> $(RUN)/backend.log 2>&1 & echo $$! > $(RUN)/backend.pid
	@for i in $$(seq 1 40); do \
		curl -sf http://127.0.0.1:$(BACKEND_PORT)/healthz >/dev/null && break || sleep 0.25; \
	done
	@curl -sf http://127.0.0.1:$(BACKEND_PORT)/healthz \
		&& echo "  ready → http://localhost:$(BACKEND_PORT)/healthz" \
		|| { echo "  FAILED — see $(RUN)/backend.log"; tail -20 $(RUN)/backend.log; exit 1; }

## ---------------------------------------------------------------- stop

down: stop-frontend stop-backend emulator-kill
	@echo "stopped"

# Wait until the port is actually free so the next start cannot race the old
# process and fail with EADDRINUSE.
define free_port
	@-lsof -ti tcp:$(1) 2>/dev/null | xargs kill 2>/dev/null || true
	@for i in $$(seq 1 40); do \
		lsof -ti tcp:$(1) >/dev/null 2>&1 || break; \
		[ $$i -eq 20 ] && lsof -ti tcp:$(1) 2>/dev/null | xargs kill -9 2>/dev/null; \
		sleep 0.25; \
	done
endef

stop-frontend:
	@-[ -f $(RUN)/frontend.pid ] && kill $$(cat $(RUN)/frontend.pid) 2>/dev/null; rm -f $(RUN)/frontend.pid
	$(call free_port,$(FRONTEND_PORT))

stop-backend:
	@-[ -f $(RUN)/backend.pid ] && kill $$(cat $(RUN)/backend.pid) 2>/dev/null; rm -f $(RUN)/backend.pid
	$(call free_port,$(BACKEND_PORT))

status:
	@printf "  frontend :%s  " $(FRONTEND_PORT); \
		lsof -ti tcp:$(FRONTEND_PORT) >/dev/null 2>&1 && echo "up" || echo "down"
	@printf "  backend  :%s  " $(BACKEND_PORT); \
		curl -sf http://127.0.0.1:$(BACKEND_PORT)/healthz >/dev/null 2>&1 && echo "up" || echo "down"

logs:
	@tail -f $(RUN)/backend.log $(RUN)/frontend.log

## ---------------------------------------------------------------- test

test: test-kotlin test-codegen test-web test-backend
	@echo ""
	@echo "  all suites green"

test-kotlin:
	@echo "→ kotlin"
	@./gradlew -q :packages:design-contract:test \
		:apps:android:data:testDebugUnitTest :apps:android:app:testDebugUnitTest

test-codegen:
	@echo "→ codegen"
	@cd packages/codegen && npm test

test-web:
	@echo "→ web"
	@cd apps/web && npm test

test-backend:
	@echo "→ backend"
	@./gradlew -q :apps:backend:test

# Integrated: the contract goes in over HTTP and real artifacts come back.
# This is the only target that proves the two halves agree at runtime.
e2e: export IKK_DATABASE_URL := jdbc:h2:mem:ikk_e2e;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE
e2e: up
	@trap '$(MAKE) --no-print-directory down >/dev/null' EXIT; \
	echo "→ e2e"; \
	status=$$(curl -sS -o $(RUN)/e2e-put.json -w '%{http_code}' \
		-X PUT http://127.0.0.1:$(BACKEND_PORT)/v1/projects/demo/contract \
		-H 'content-type: application/json' \
		--data-binary @packages/codegen/examples/home.json); \
	[ "$$status" = 200 ] \
		|| { echo "  PUT failed ($$status)"; cat $(RUN)/e2e-put.json; exit 1; }; \
	curl -sf -X POST http://127.0.0.1:$(BACKEND_PORT)/v1/projects/demo/generate \
		| python3 -c "import json,sys; d=json.load(sys.stdin); \
			names=[a['name'] for a in d['artifacts']]; \
			assert any(n.endswith('.kt') for n in names), names; \
			assert any(n.endswith('.css') for n in names), names; \
			print('  ok', d['checkpoint'], '->', ', '.join(names))"

generate:
	@cd packages/codegen && npm run generate

check:
	@cd packages/codegen && npm run check

## ---------------------------------------------------------------- emulator

AVD      ?= hack36
SDK      := $(HOME)/Library/Android/sdk
EMULATOR := $(SDK)/emulator/emulator

# Boot the AVD if nothing is attached. Idempotent: running it twice does not
# start a second emulator, so `make demo` is safe to re-run mid-presentation.
#
# Cold boot (-no-snapshot-load) on purpose. A snapshot restores whatever the
# last run left installed, which is exactly the stale-APK confusion the demo
# must not hit.
emulator:
	@if $(ADB) get-state >/dev/null 2>&1; then \
		echo "  emulator already running"; \
	else \
		$(EMULATOR) -list-avds | grep -qx "$(AVD)" \
			|| { echo "  no AVD named '$(AVD)' — $(EMULATOR) -list-avds"; exit 1; }; \
		echo "-> emulator $(AVD) (cold boot)"; \
		nohup $(EMULATOR) -avd $(AVD) -no-snapshot-load -no-boot-anim \
			> $(RUN)/emulator.log 2>&1 & \
		$(ADB) wait-for-device; \
		for i in $$(seq 1 120); do \
			[ "$$($(ADB) shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ] && break; \
			sleep 2; \
		done; \
		[ "$$($(ADB) shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ] \
			&& echo "  booted" \
			|| { echo "  FAILED to boot — see $(RUN)/emulator.log"; exit 1; }; \
	fi

# Kill every attached emulator and wait until adb really reports none. Without
# the wait, the next `make emulator` sees a dying device and attaches to it.
emulator-kill:
	@if $(ADB) get-state >/dev/null 2>&1; then \
		echo "-> stopping emulator"; \
		for s in $$($(ADB) devices | awk '/^emulator-/ {print $$1}'); do \
			$(ADB) -s $$s emu kill >/dev/null 2>&1 || true; \
		done; \
		for i in $$(seq 1 40); do \
			$(ADB) get-state >/dev/null 2>&1 || break; \
			sleep 0.5; \
		done; \
		$(ADB) get-state >/dev/null 2>&1 \
			&& { echo "  still up — killing the process"; pkill -f "qemu-system.*$(AVD)" || true; } \
			|| echo "  stopped"; \
	fi

# A guaranteed-clean device: no leftover APK, no leftover state.
emulator-reset: emulator-kill
	@echo "-> emulator $(AVD) (wipe-data cold boot)"
	@nohup $(EMULATOR) -avd $(AVD) -no-snapshot-load -wipe-data -no-boot-anim \
		> $(RUN)/emulator.log 2>&1 &
	@$(ADB) wait-for-device
	@for i in $$(seq 1 120); do \
		[ "$$($(ADB) shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ] && break; \
		sleep 2; \
	done
	@echo "  wiped and booted"

## ---------------------------------------------------------------- demo

# Everything the pitch needs, in the order it is presented.
demo: $(RUN) backend frontend emulator
	@echo ""
	@echo "  editor   $(EDITOR_URL)"
	@echo "  then     make android-sync   (regenerate -> install -> relaunch)"
	@echo ""

## ---------------------------------------------------------------- android

PROJECT     ?= demo
ANDROID_GEN := apps/android/app/src/main/kotlin/com/ikk/ui/generated/HomeLayout.generated.kt
ADB         := $(shell command -v adb 2>/dev/null || echo $$HOME/Library/Android/sdk/platform-tools/adb)

# Contract -> generated Compose -> APK on the device.
#
# Deterministic end to end: the contract fully determines the Kotlin, so a
# colour change is a regeneration, not a decision. Nothing in this path needs
# a model. Run `make backend` and edit in the web editor first.
android-sync: $(RUN)
	@echo "-> generate"
	@curl -sf -X POST http://127.0.0.1:$(BACKEND_PORT)/v1/projects/$(PROJECT)/generate \
		-o $(RUN)/generate.json \
		|| { echo "  backend unreachable on :$(BACKEND_PORT) - run 'make backend'"; exit 1; }
	@mkdir -p $(dir $(ANDROID_GEN))
# Download to a temp file and refuse an empty body: a failed fetch that
# writes straight to the source file silently empties it, and the next
# build fails somewhere unrelated.
	@curl -sf http://127.0.0.1:$(BACKEND_PORT)/v1/projects/$(PROJECT)/artifacts/HomeLayout.generated.kt \
		-o $(ANDROID_GEN).tmp \
		|| { rm -f $(ANDROID_GEN).tmp; echo "  artifact fetch failed"; exit 1; }
	@test -s $(ANDROID_GEN).tmp \
		|| { rm -f $(ANDROID_GEN).tmp; echo "  empty artifact - refusing to overwrite"; exit 1; }
	@mv $(ANDROID_GEN).tmp $(ANDROID_GEN)
	@head -1 $(ANDROID_GEN) | sed 's|// GENERATED FROM |  |'
	@$(MAKE) --no-print-directory android-install

# Build and launch whatever is currently in $(ANDROID_GEN). Split out so the
# offline path works too: `make generate && make android-install`.
android-install:
	@$(ADB) get-state >/dev/null 2>&1 \
		|| { echo "  no device - start an emulator first"; exit 1; }
	@./gradlew -q :apps:android:app:installDebug
	@$(ADB) shell am force-stop com.ikk
	@$(ADB) shell am start -n com.ikk/.MainActivity >/dev/null
	@echo "  installed and launched"

## ---------------------------------------------------------------- docker

docker-build:
	@docker build -t ikk-backend:dev -f apps/backend/Dockerfile .

docker-run: docker-build
	@docker run --rm -p $(BACKEND_PORT):8000 --name ikk-backend ikk-backend:dev

## ---------------------------------------------------------------- clean

clean: down
	@./gradlew clean
	@rm -rf $(RUN)
	@echo "cleaned"
