import os

import wikipedia
import wikipedia.exceptions
from dotenv import load_dotenv
from google.adk.agents import Agent
from google.adk.models.lite_llm import LiteLlm
from google.adk.tools import FunctionTool

load_dotenv()


def search_wikipedia(query: str, top_k: int = 5) -> str:
    """
    Search Wikipedia for a query.
    Returns a string containing formatted search results.
    """
    MAX_QUERY_LENGTH = 100
    contents = []

    try:
        page_titles = wikipedia.search(query[:MAX_QUERY_LENGTH], results=top_k)
    except wikipedia.exceptions.WikipediaException as e:
        print(f"Wikipedia API error: {e}")
        return ""

    for page_title in page_titles:
        try:
            wiki_page = wikipedia.page(title=page_title, auto_suggest=False)
        except (
            wikipedia.exceptions.PageError,
            wikipedia.exceptions.DisambiguationError,
            wikipedia.exceptions.WikipediaException,
        ) as e:
            print(f"Failed to fetch page '{page_title}': {e}")
            continue

        main_meta = {
            "title": page_title,
            "summary": wiki_page.summary,
            "source": wiki_page.url,
        }
        add_meta = {
            "categories": ", ".join(wiki_page.categories),
            "page_url": wiki_page.url,
            "image_urls": ", ".join(wiki_page.images),
            "related_titles": ", ".join(wiki_page.links),
            "parent_id": wiki_page.parent_id,
            "references": ", ".join(wiki_page.references),
            "revision_id": wiki_page.revision_id,
            "sections": ", ".join(wiki_page.sections),
        }

        contents.append({
            **main_meta,
            **add_meta,
            "content": wiki_page.content
        })

    result = ""
    for item in contents:
        item_str = ""
        for key, value in item.items():
            item_str += f"{key}: {value}\n"
        result += item_str + "\n" + "-"*50 + "\n"

    return result.strip()


root_agent = Agent(
    name="facts_agent",
    model=LiteLlm(model="openai/" + os.getenv('OPENAI_API_MODEL', 'gpt-4o')),
    description="Agent to give interesting facts.",
    instruction="You are a helpful agent who can provide interesting facts.",
    tools=[FunctionTool(search_wikipedia)],
)
