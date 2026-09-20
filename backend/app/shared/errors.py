"""One error shape for every non-2xx response. See docs/api.md."""

from fastapi import Request
from fastapi.responses import JSONResponse


class ApiError(Exception):
    status: int = 400
    code: str = "bad_request"

    def __init__(self, message: str, status: int | None = None, code: str | None = None):
        super().__init__(message)
        self.message = message
        if status is not None:
            self.status = status
        if code is not None:
            self.code = code


class NotFound(ApiError):
    status = 404
    code = "not_found"


class StaleVersion(ApiError):
    status = 409
    code = "stale_version"


class DirtyContract(ApiError):
    status = 409
    code = "dirty_contract"


class ContractInvalid(ApiError):
    status = 422
    code = "contract_invalid"

    def __init__(self, message: str, violations: list[dict] | None = None):
        super().__init__(message)
        self.violations = violations or []


async def api_error_handler(request: Request, exc: ApiError) -> JSONResponse:
    body = {
        "error": exc.code,
        "message": exc.message,
        "requestId": getattr(request.state, "request_id", None),
    }
    if isinstance(exc, ContractInvalid):
        body["violations"] = exc.violations
    return JSONResponse(status_code=exc.status, content=body)
