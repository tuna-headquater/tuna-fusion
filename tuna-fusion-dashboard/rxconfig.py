import reflex as rx

config = rx.Config(
    app_name="tuna_fusion_dashboard",
    plugins=[
        rx.plugins.SitemapPlugin(),
        rx.plugins.TailwindV4Plugin(),
    ],
)