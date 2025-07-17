from fastapi import Request, Response

def handle(req: Request) -> Response:
    return Response(status_code=200, content="hello")