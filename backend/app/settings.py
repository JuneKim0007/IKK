from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_prefix="IKK_", env_file=".env")

    database_url: str = "sqlite:///./ikk.db"
    artifact_dir: str = "./artifacts"
    asset_dir: str = "./assets"
    max_asset_bytes: int = 8 * 1024 * 1024
    api_prefix: str = "/v1"


settings = Settings()
