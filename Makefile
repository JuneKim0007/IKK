# IKK — one machine, two surfaces, no database.
#
#   make up        start backend + frontend
#   make down      stop both
#   make test      every suite: Kotlin, Node codegen, Python
#
# Ports: frontend 5173, backend 8000. Override with FRONTEND_PORT / BACKEND_PORT.

SHELL        := /bin/bash
FRONTEND_PORT ?= 5173
BACKEND_PORT  ?= 8000
EDITOR        := apps/web/index.html
EDITOR_URL     = http://localhost:$(FRONTEND_PORT)/$(EDITOR)?api=http://127.0.0.1:$(BACKEND_PORT)
VENV          := apps/backend/.venv
PY            := $(VENV)/bin/python
RUN           := .run

.DEFAULT_GOAL := help
.PHONY: help up down frontend backend stop-frontend stop-backend status logs \
        test test-kotlin test-codegen test-web test-backend e2e generate check clean venv \
        docker-build docker-run

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
	@echo "  make clean          stop everything, drop venv and logs"
	@echo ""

## ---------------------------------------------------------------- run

up: backend frontend
	@echo ""
	@echo "  editor   $(EDITOR_URL)"
	@echo "  backend  http://localhost:$(BACKEND_PORT)/docs"
	@echo ""
	@echo "  make down   to stop"

$(RUN):
	@mkdir -p $(RUN)

# Served from the repo ROOT, not apps/web, so the editor can fetch
# docs/fixtures/ from the same origin. Native ES modules, no bundler, no install.
frontend: $(RUN) stop-frontend
	@echo "→ frontend on :$(FRONTEND_PORT)"
	@python3 -m http.server $(FRONTEND_PORT) --bind 127.0.0.1 \
		> $(RUN)/frontend.log 2>&1 & echo $$! > $(RUN)/frontend.pid
	@for i in $$(seq 1 40); do \
		curl -sf -o /dev/null http://127.0.0.1:$(FRONTEND_PORT)/$(EDITOR) && break || sleep 0.25; \
	done
	@curl -sf -o /dev/null http://127.0.0.1:$(FRONTEND_PORT)/$(EDITOR) \
		&& echo "  editor  $(EDITOR_URL)" \
		|| { echo "  FAILED — see $(RUN)/frontend.log"; exit 1; }
	@-command -v open >/dev/null && open "$(EDITOR_URL)" >/dev/null 2>&1 || true

backend: venv $(RUN) stop-backend
	@echo "→ backend on :$(BACKEND_PORT)"
	@cd apps/backend && ../../$(VENV)/bin/uvicorn app.main:app \
		--host 127.0.0.1 --port $(BACKEND_PORT) --reload \
		> ../../$(RUN)/backend.log 2>&1 & echo $$! > $(RUN)/backend.pid
	@for i in $$(seq 1 40); do \
		curl -sf http://127.0.0.1:$(BACKEND_PORT)/healthz >/dev/null && break || sleep 0.25; \
	done
	@curl -sf http://127.0.0.1:$(BACKEND_PORT)/healthz \
		&& echo "  ready → http://localhost:$(BACKEND_PORT)/docs" \
		|| { echo "  FAILED — see $(RUN)/backend.log"; tail -20 $(RUN)/backend.log; exit 1; }

## ---------------------------------------------------------------- stop

down: stop-frontend stop-backend
	@echo "stopped"

# Killing the pid is not enough: uvicorn --reload runs a reloader plus a child,
# and the port stays bound for a moment after. Wait for it to actually free or
# the next start races and dies with EADDRINUSE.
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

## ---------------------------------------------------------------- python

venv: $(VENV)/.installed

$(VENV)/.installed: apps/backend/pyproject.toml
	@echo "→ python env"
	@python3 -m venv $(VENV)
	@$(PY) -m pip install -q --upgrade pip
	@cd apps/backend && ../../$(VENV)/bin/pip install -q -e ".[dev]"
	@touch $(VENV)/.installed

## ---------------------------------------------------------------- test

test: test-kotlin test-codegen test-web test-backend
	@echo ""
	@echo "  all suites green"

test-kotlin:
	@echo "→ kotlin"
	@./gradlew -q test

test-codegen:
	@echo "→ codegen"
	@cd packages/codegen && npm test

test-web:
	@echo "→ web"
	@cd apps/web && npm test

test-backend: venv
	@echo "→ backend"
	@cd apps/backend && ../../$(VENV)/bin/pytest -q

# Integrated: the contract goes in over HTTP and real artifacts come back.
# This is the only target that proves the two halves agree at runtime.
e2e: up
	@echo "→ e2e"
	@curl -sf -X PUT http://127.0.0.1:$(BACKEND_PORT)/v1/projects/demo/contract \
		-H 'content-type: application/json' \
		--data-binary @packages/codegen/examples/home.json > /dev/null \
		|| { echo "  PUT failed"; exit 1; }
	@curl -sf -X POST http://127.0.0.1:$(BACKEND_PORT)/v1/projects/demo/generate \
		| python3 -c "import json,sys; d=json.load(sys.stdin); \
			names=[a['name'] for a in d['artifacts']]; \
			assert any(n.endswith('.kt') for n in names), names; \
			assert any(n.endswith('.css') for n in names), names; \
			print('  ok', d['checkpoint'], '->', ', '.join(names))"
	@$(MAKE) --no-print-directory down

generate:
	@cd packages/codegen && npm run generate

check:
	@cd packages/codegen && npm run check

## ---------------------------------------------------------------- docker

docker-build:
	@docker build -t ikk-backend:dev -f apps/backend/Dockerfile .

docker-run: docker-build
	@docker run --rm -p $(BACKEND_PORT):8000 --name ikk-backend ikk-backend:dev

## ---------------------------------------------------------------- clean

clean: down
	@rm -rf $(VENV) $(RUN)
	@echo "cleaned"
