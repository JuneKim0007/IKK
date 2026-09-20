package com.ikk.backend.features.health

/**
 * A contract carrying **one node of every type**, plus a gradient background.
 *
 * Readiness round-trips this through the same decoder and validator real
 * requests use. When a type is added to the spec and the validator is not
 * taught about it, /healthz goes red at startup rather than on whichever
 * request first happens to use that type — which is exactly how triangle and
 * line reached the backend as "unknown key" errors.
 */
object ContractCanary {
    const val NODE_COUNT = 6

    val TYPES = listOf("rect", "ellipse", "triangle", "line", "text", "image")

    val JSON = """
    {
      "schemaVersion": 1,
      "checkpoint": "cp_000",
      "screen": "Canary",
      "reference": { "w": 375, "h": 667, "unit": "dp" },
      "layout": "relative",
      "background": { "fill": { "type": "linear", "angle": 135.0,
        "stops": [ { "color": "#F49AA8", "at": 0.0 }, { "color": "#C86DD7", "at": 100.0 } ] } },
      "components": {
        "rect_a": { "id": "c1", "type": "rect", "name": "A", "z": 0, "visible": true,
          "opacity": 1.0, "rect": { "x": 0.0, "y": 0.0, "w": 10.0, "h": 10.0, "unit": "%" },
          "fill": "#65558F", "stroke": null, "radius": 4, "text": null,
          "version": 1, "updatedAt": "2026-01-01T00:00:00Z" },
        "ellipse_b": { "id": "c2", "type": "ellipse", "name": "B", "z": 1, "visible": true,
          "opacity": 1.0, "rect": { "x": 0.0, "y": 12.0, "w": 10.0, "h": 10.0, "unit": "%" },
          "fill": "#2E8B74", "stroke": null, "radius": "50%", "text": null,
          "version": 1, "updatedAt": "2026-01-01T00:00:00Z" },
        "triangle_c": { "id": "c3", "type": "triangle", "name": "C", "z": 2, "visible": true,
          "opacity": 1.0, "rect": { "x": 0.0, "y": 24.0, "w": 10.0, "h": 10.0, "unit": "%" },
          "fill": "#B4772A", "stroke": null, "radius": 0, "text": null,
          "version": 1, "updatedAt": "2026-01-01T00:00:00Z" },
        "line_d": { "id": "c4", "type": "line", "name": "D", "z": 3, "visible": true,
          "opacity": 1.0, "rect": { "x": 0.0, "y": 36.0, "w": 10.0, "h": 2.0, "unit": "%" },
          "fill": null, "stroke": { "color": "#5E6462", "width": 2.0 }, "radius": 0, "text": null,
          "line": { "orientation": "topLeftToBottomRight" },
          "version": 1, "updatedAt": "2026-01-01T00:00:00Z" },
        "text_e": { "id": "c5", "type": "text", "name": "E", "z": 4, "visible": true,
          "opacity": 1.0, "rect": { "x": 0.0, "y": 40.0, "w": 40.0, "h": 5.0, "unit": "%" },
          "fill": null, "stroke": null, "radius": 0,
          "text": { "value": "canary", "size": 14.0, "align": "start", "color": "#1B1D1C",
            "weight": 400, "lineHeight": null, "fontFamily": "Roboto", "maxLines": null },
          "version": 1, "updatedAt": "2026-01-01T00:00:00Z" },
        "image_f": { "id": "c6", "type": "image", "name": "F", "z": 5, "visible": true,
          "opacity": 1.0, "rect": { "x": 0.0, "y": 48.0, "w": 40.0, "h": 20.0, "unit": "%" },
          "fill": null, "stroke": null, "radius": 0, "text": null,
          "source": null, "contentScale": "crop", "alt": "canary",
          "version": 1, "updatedAt": "2026-01-01T00:00:00Z" }
      }
    }
    """.trimIndent()
}
