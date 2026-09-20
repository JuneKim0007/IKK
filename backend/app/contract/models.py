"""Pydantic mirror of docs/json_contract.md.

That document is normative. If this file disagrees with it, this file is wrong.

`extra="forbid"` everywhere is validation rule V11 and is deliberate: a field
one implementation writes and another silently drops is a divergence that only
shows up at a demo.
"""

from __future__ import annotations

import re
from datetime import datetime
from typing import Annotated, Literal

from pydantic import BaseModel, ConfigDict, Field, field_validator

SCHEMA_VERSION = 1

_COLOR = re.compile(r"^#[0-9A-F]{6}([0-9A-F]{2})?$")
_KEY = re.compile(r"^(rect|ellipse|text|image)_\d+$")


class Strict(BaseModel):
    model_config = ConfigDict(extra="forbid", frozen=True)


Color = Annotated[str, Field(pattern=r"^#[0-9A-F]{6}([0-9A-F]{2})?$")]
Radius = float | int | Literal["50%"]


def to_compose_color(hex_value: str) -> str:
    """CSS is #RRGGBBAA, Compose is 0xAARRGGBB. The byte order differs."""
    if len(hex_value) == 7:
        return f"0xFF{hex_value[1:]}"
    return f"0x{hex_value[7:9]}{hex_value[1:7]}"


class RelRect(Strict):
    """docs/json_contract.md §5 — percentages, one decimal place."""

    x: float
    y: float
    w: float
    h: float
    unit: Literal["%"] = "%"

    # Compose consumes fractions; CSS consumes percentages. Dividing by 100 in
    # exactly one of the two generators is the likeliest arithmetic bug here.
    @property
    def x_fraction(self) -> float:
        return round(self.x / 100.0, 5)

    @property
    def y_fraction(self) -> float:
        return round(self.y / 100.0, 5)

    @property
    def w_fraction(self) -> float:
        return round(self.w / 100.0, 5)

    @property
    def h_fraction(self) -> float:
        return round(self.h / 100.0, 5)


class Stroke(Strict):
    """§7 — width is dp. Alignment is INSIDE on both targets."""

    color: Color
    width: float


class TextPayload(Strict):
    """§9 — `null` payload means no text slot; an empty `value` means a slot."""

    value: str
    size: float
    align: Literal["start", "center", "end"]
    color: Color
    weight: Literal["normal", "medium", "semibold"]
    maxLines: int | None = None

    LINE_HEIGHT_RATIO = 1.3

    @property
    def line_height(self) -> float:
        return round(self.size * self.LINE_HEIGHT_RATIO, 2)


class AssetRef(Strict):
    ref: str
    mime: str


class NodeBase(Strict):
    """§4 — every node carries the full envelope, whatever its type."""

    id: str
    z: int
    visible: bool = True
    opacity: float = 1.0
    rect: RelRect
    fill: Color | None = None
    stroke: Stroke | None = None
    radius: Radius = 0
    text: TextPayload | None = None
    version: int = 1
    updatedAt: datetime

    @property
    def key(self) -> str:
        digits = "".join(c for c in self.id if c.isdigit()) or self.id
        return f"{self.type}_{digits}"  # type: ignore[attr-defined]

    # Capability predicates — which inspector controls a surface should show.
    accepts_text: bool = True
    accepts_fill: bool = True
    radius_editable: bool = True


class RectNode(NodeBase):
    type: Literal["rect"] = "rect"

    @field_validator("radius")
    @classmethod
    def _not_full(cls, v: Radius) -> Radius:
        if v == "50%":
            raise ValueError('a rect cannot have radius "50%" — use an ellipse')
        return v


class EllipseNode(NodeBase):
    type: Literal["ellipse"] = "ellipse"
    radius: Radius = "50%"
    radius_editable: bool = False

    @field_validator("radius")
    @classmethod
    def _must_be_full(cls, v: Radius) -> Radius:
        if v != "50%":
            raise ValueError('an ellipse must have radius "50%"')
        return v


class TextNode(NodeBase):
    type: Literal["text"] = "text"
    text: TextPayload
    accepts_fill: bool = False
    radius_editable: bool = False

    @field_validator("fill", "stroke")
    @classmethod
    def _bare(cls, v: object) -> object:
        if v is not None:
            raise ValueError("a text node carries no fill or stroke")
        return v


class ImageNode(NodeBase):
    type: Literal["image"] = "image"
    source: AssetRef
    contentScale: Literal["crop", "fit", "fill"] = "crop"
    alt: str = ""
    accepts_text: bool = False
    accepts_fill: bool = False

    @field_validator("text")
    @classmethod
    def _no_text(cls, v: TextPayload | None) -> None:
        if v is not None:
            raise ValueError("an image node cannot carry text")
        return None


DesignNode = Annotated[
    RectNode | EllipseNode | TextNode | ImageNode,
    Field(discriminator="type"),
]


class Reference(Strict):
    w: int = 375
    h: int = 667
    unit: Literal["dp"] = "dp"


class Contract(Strict):
    """§3."""

    schemaVersion: int = SCHEMA_VERSION
    checkpoint: str
    screen: str
    reference: Reference = Reference()
    layout: Literal["relative"] = "relative"
    components: dict[str, DesignNode]

    def paint_order(self) -> list[DesignNode]:
        """z ascending — never map iteration order."""
        return sorted(self.components.values(), key=lambda n: (n.z, n.id))

    def emittable(self) -> list[DesignNode]:
        """Hidden nodes stay in the contract and are not emitted."""
        return [n for n in self.paint_order() if n.visible]


class UnsupportedSchemaVersion(Exception):
    def __init__(self, found: int):
        super().__init__(
            f"contract schemaVersion {found} is not supported "
            f"(expected {SCHEMA_VERSION}). Refusing to guess."
        )
        self.found = found


def parse_contract(raw: dict) -> Contract:
    """§2 — an unknown schemaVersion is rejected, never guessed."""
    found = raw.get("schemaVersion")
    if found != SCHEMA_VERSION:
        raise UnsupportedSchemaVersion(found if isinstance(found, int) else -1)
    return Contract.model_validate(raw)


def is_valid_key(key: str) -> bool:
    return bool(_KEY.match(key))


def is_valid_color(value: str) -> bool:
    return bool(_COLOR.match(value))
