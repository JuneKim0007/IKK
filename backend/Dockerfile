# Node is in the image because codegen/ is the single emitter and the backend
# shells out to it. Re-implementing it in Python would mean two emitters that
# have to stay byte-identical forever.
FROM python:3.12-slim

RUN apt-get update \
 && apt-get install -y --no-install-recommends nodejs \
 && rm -rf /var/lib/apt/lists/*

WORKDIR /srv

COPY backend/pyproject.toml ./backend/pyproject.toml
RUN pip install --no-cache-dir -e ./backend

COPY backend ./backend
COPY codegen ./codegen

ENV PYTHONUNBUFFERED=1
EXPOSE 8000
WORKDIR /srv/backend

# No database: contracts live in memory for the life of the process.
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
