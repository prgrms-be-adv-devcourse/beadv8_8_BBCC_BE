"""Opt-in paid adapter for the same bounded operational note contract."""
import time

from groq_provider import ProviderError, StructuredChatProvider


class OpenAIProvider(StructuredChatProvider):
    name = 'OpenAI'
    endpoint = 'https://api.openai.com/v1/chat/completions'
    model = 'gpt-4.1-mini-2025-04-14'
    max_tokens = 600
    options = {'store': False}

    def __init__(self, api_key, *, paid_confirmed=False, clock=time.time, transport=None):
        if paid_confirmed is not True:
            raise ProviderError('paid_confirmation_required')
        super().__init__(api_key, clock=clock, transport=transport)
