"""Fixed public error codes; never forward upstream exception strings."""
CODES = {'rate_limited', 'capacity_reached', 'conflict', 'not_found', 'forbidden',
         'deadline_exceeded', 'request_timeout', 'collection_unavailable',
         'delivery_unavailable', 'storage_unavailable', 'invalid_request', 'unavailable'}


class NotesError(Exception):
    def __init__(self, code):
        self.code = code if code in CODES else 'unavailable'
        super().__init__(self.code)
