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
VENV          := backend/.venv
PY            := $(VENV)/bin/python
RUN           := .run

.DEFAULT_GOAL := help
.PHONY: help up down frontend backend stop-frontend stop-backend status logs \
        test test-kotlin test-codegen test-backend generate check clean venv \
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
	@echo "  make frontend       static server on :$(FRONTEND_PORT)"
	@echo "  make backend        FastAPI on :$(BACKEND_PORT)"
	@echo ""
	@echo "  make test           Kotlin + codegen + backend"
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
	@echo "  frontend  http://localhost:$(FRONTEND_PORT)/"
	@echo "  backend   http://localhost:$(BACKEND_PORT)/docs"
	@echo ""
	@echo "  make down   to stop"

$(RUN):
	@mkdir -p $(RUN)

frontend: $(RUN) stop-frontend
	@echo "→ frontend on :$(FRONTEND_PORT)"
	@python3 -m http.server $(FRONTEND_PORT) --bind 127.0.0.1 \
		> $(RUN)/frontend.log 2>&1 & echo $$! > $(RUN)/frontend.pid
	@sleep 1
	@echo "  prototypes  http://localhost:$(FRONTEND_PORT)/prototype/"
	@echo "  web editor  http://localhost:$(FRONTEND_PORT)/prototype/editor-web/"
	@echo "  android     http://localhost:$(FRONTEND_PORT)/prototype/editor-android/"
	@-command -v open >/dev/null && open "http://localhost:$(FRONTEND_PORT)/prototype/editor-web/" || true

backend: venv $(RUN) stop-backend
	@echo "→ backend on :$(BACKEND_PORT)"
	@cd backend && ../$(VENV)/bin/uvicorn app.main:app \
		--host 127.0.0.1 --port $(BACKEND_PORT) --reload \
		> ../$(RUN)/backend.log 2>&1 & echo $$! > $(RUN)/backend.pid
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

$(VENV)/.installed: backend/pyproject.toml
	@echo "→ python env"
	@python3 -m venv $(VENV)
	@$(PY) -m pip install -q --upgrade pip
	@cd backend && ../$(VENV)/bin/pip install -q -e ".[dev]"
	@touch $(VENV)/.installed

## ---------------------------------------------------------------- test

test: test-kotlin test-codegen test-backend
	@echo ""
	@echo "  all suites green"

test-kotlin:
	@echo "→ kotlin"
	@./gradlew -q test

test-codegen:
	@echo "→ codegen"
	@cd codegen && npm test

test-backend: venv
	@echo "→ backend"
	@cd backend && ../$(VENV)/bin/pytest -q

generate:
	@cd codegen && npm run generate

check:
	@cd codegen && npm run check

## ---------------------------------------------------------------- docker

docker-build:
	@docker build -t ikk-backend:dev backend

docker-run: docker-build
	@docker run --rm -p $(BACKEND_PORT):8000 --name ikk-backend ikk-backend:dev

## ---------------------------------------------------------------- clean

clean: down
	@rm -rf $(VENV) $(RUN)
	@echo "cleaned"
