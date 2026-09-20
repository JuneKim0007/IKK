/* editor-core.js
 *
 * One node model, one contract, one set of emitters — shared verbatim by the
 * web editor and the Android editor prototype. If the two surfaces ever
 * disagree about geometry, it is a bug in one of the renderers, not a
 * difference of opinion about the design.
 */
(function (global) {
  "use strict";

  var W = 375, H = 667;                       // reference viewport, dp

  var PALETTE = [
    "#65558F", "#4C6FBF", "#2E8B74", "#B4772A",
    "#C0504D", "#5E6462", "#E8E3F0", "#FFFFFF"
  ];

  var seq = 1;
  function nextId() { return "n" + (seq++); }

  var DEF = {
    rect:    { name: "Rectangle", fill: "#E8E3F0", stroke: "#B5AFBC", strokeW: 1, radius: 8,  text: null, w: 160, h: 56 },
    ellipse: { name: "Ellipse",   fill: "#E8E3F0", stroke: "#B5AFBC", strokeW: 1, radius: 0,  text: null, w: 96,  h: 96 },
    text:    { name: "Text",      fill: "none",    stroke: "none",    strokeW: 0, radius: 0,  text: "Text", w: 180, h: 28 },
    image:   { name: "Image",     fill: "#DFDAE6", stroke: "none",    strokeW: 0, radius: 8,  text: "image", w: 180, h: 101 }
  };

  function make(type, x, y) {
    var d = DEF[type] || DEF.rect;
    return {
      id: nextId(), type: type, name: d.name,
      x: x, y: y, w: 1, h: 1,
      fill: d.fill, stroke: d.stroke, strokeW: d.strokeW, radius: d.radius,
      text: d.text, fontSize: type === "text" ? 18 : 15,
      align: "left", textColor: "#1B1D1C",
      visible: true, opacity: 1
    };
  }

  function node(type, name, x, y, w, h, extra) {
    var n = make(type, x, y);
    n.name = name; n.w = w; n.h = h;
    for (var k in extra) if (Object.prototype.hasOwnProperty.call(extra, k)) n[k] = extra[k];
    return n;
  }

  function seed() {
    seq = 1;
    return [
      node("rect", "Header", 0, 0, 375, 180, { fill: "#65558F", stroke: "none", strokeW: 0, radius: 0 }),
      node("text", "Title", 28, 74, 260, 34, { text: "Good morning", fontSize: 26, textColor: "#FFFFFF" }),
      node("text", "Subtitle", 28, 112, 280, 22, { text: "Three things need you today.", fontSize: 14, textColor: "#E5DEF5" }),
      node("rect", "Card", 24, 212, 327, 110, { fill: "#FFFFFF", stroke: "#DAD5E2", strokeW: 1, radius: 14,
        text: "Draft review\nDue 4:00 PM", fontSize: 15, textColor: "#1B1D1C" }),
      node("image", "Photo", 24, 342, 327, 150, { text: "cover", radius: 14 }),
      node("ellipse", "Badge", 294, 178, 56, 56, { fill: "#2E8B74", stroke: "none", strokeW: 0,
        text: "3", fontSize: 22, align: "center", textColor: "#FFFFFF" }),
      node("rect", "Primary", 24, 520, 327, 50, { fill: "#4C6FBF", stroke: "none", strokeW: 0, radius: 25,
        text: "Open inbox", fontSize: 16, align: "center", textColor: "#FFFFFF" })
    ];
  }

  function byId(nodes, id) {
    for (var i = 0; i < nodes.length; i++) if (nodes[i].id === id) return nodes[i];
    return null;
  }

  /* ---------- rendering, shared by both surfaces ---------- */

  function applyBox(el, n, z) {
    el.style.left = (n.x * z) + "px";
    el.style.top = (n.y * z) + "px";
    el.style.width = (n.w * z) + "px";
    el.style.height = (n.h * z) + "px";
    el.style.opacity = n.opacity;
    el.style.background = n.fill === "none" ? "transparent" : n.fill;
    el.style.border = (n.stroke === "none" || !n.strokeW)
      ? "none"
      : (Math.max(1, n.strokeW * z) + "px solid " + n.stroke);
    el.style.borderRadius = n.type === "ellipse" ? "50%" : ((n.radius * z) + "px");
    el.style.alignItems = "center";
  }

  function applyText(t, n, z) {
    t.style.fontSize = (n.fontSize * z) + "px";
    t.style.color = n.textColor;
    t.style.textAlign = n.align;
    t.style.lineHeight = "1.3";
    t.style.fontWeight = n.fontSize >= 22 ? "600" : "400";
  }

  /* ---------- move / resize, shared ---------- */

  function transform(drag, dx, dy, w, h) {
    var n = drag.n, d = drag.dir;
    if (!d) {
      n.x = Math.round(Math.max(-n.w + 12, Math.min(drag.ox + dx, w - 12)));
      n.y = Math.round(Math.max(-n.h + 12, Math.min(drag.oy + dy, h - 12)));
      return;
    }
    if (d.indexOf("e") >= 0) n.w = Math.max(4, Math.round(drag.ow + dx));
    if (d.indexOf("s") >= 0) n.h = Math.max(4, Math.round(drag.oh + dy));
    if (d.indexOf("w") >= 0) { n.w = Math.max(4, Math.round(drag.ow - dx)); n.x = Math.round(drag.ox + (drag.ow - n.w)); }
    if (d.indexOf("n") >= 0) { n.h = Math.max(4, Math.round(drag.oh - dy)); n.y = Math.round(drag.oy + (drag.oh - n.h)); }
  }

  /* ---------- the contract ---------- */

  function pct(v, of) { return Math.round((v / of) * 1000) / 10; }
  function frac(v, of) { return (Math.round((v / of) * 1000) / 1000).toFixed(3); }
  function key(n) { return n.type + "_" + n.id.replace("n", ""); }

  function contract(nodes, cp) {
    var vis = nodes.filter(function (n) { return n.visible; });
    var components = {};
    vis.forEach(function (n) {
      components[key(n)] = {
        id: n.id,
        type: n.type,
        rect: { x: pct(n.x, W), y: pct(n.y, H), w: pct(n.w, W), h: pct(n.h, H), unit: "%" },
        fill: n.fill,
        stroke: n.strokeW ? { color: n.stroke, width: n.strokeW } : null,
        radius: n.type === "ellipse" ? "50%" : n.radius,
        opacity: n.opacity,
        text: (n.text === null || n.text === undefined) ? null : {
          value: n.text, size: n.fontSize, align: n.align, color: n.textColor
        },
        updatedAt: n.updatedAt || null
      };
    });
    return {
      version: 1,
      checkpoint: "cp_" + String(cp).padStart(3, "0"),
      screen: "Home",
      reference: { w: W, h: H, unit: "dp" },
      layout: "relative",
      components: components
    };
  }

  /* ---------- emitters ---------- */

  function hex(c) { return "0xFF" + String(c).replace("#", "").toUpperCase(); }

  function toKotlin(nodes, c) {
    var vis = nodes.filter(function (n) { return n.visible; });
    var k = [];
    k.push("// GENERATED FROM contract " + c.checkpoint + " — DO NOT EDIT");
    k.push("// Rewritten wholesale on every [Generate]. Behaviour goes in Home.kt");
    k.push("");
    k.push("package com.ikk.ui.generated");
    k.push("");
    k.push("@Composable");
    k.push("fun HomeLayout(modifier: Modifier = Modifier) = BoxWithConstraints(modifier.fillMaxSize()) {");
    k.push("  // Every rect is a fraction of this box, so one contract lays out at any size.");
    k.push("  fun Modifier.rel(x: Float, y: Float, w: Float, h: Float) = this");
    k.push("    .offset(maxWidth * x, maxHeight * y)");
    k.push("    .size(maxWidth * w, maxHeight * h)");
    k.push("");
    vis.forEach(function (n) {
      var m = "Modifier.rel(" + frac(n.x, W) + "f, " + frac(n.y, H) + "f, " +
              frac(n.w, W) + "f, " + frac(n.h, H) + "f)";
      var shape = n.type === "ellipse" ? "CircleShape" : ("RoundedCornerShape(" + n.radius + ".dp)");

      if (n.type === "image") {
        k.push("  Image(");
        k.push("    painter = painterResource(R.drawable." + key(n) + "),");
        k.push('    contentDescription = "' + (n.text || "") + '",');
        k.push("    contentScale = ContentScale.Crop,");
        k.push("    modifier = " + m + ".clip(" + shape + "),");
        k.push("  )");
        return;
      }

      var mods = m;
      if (n.fill !== "none") mods += "\n      .background(Color(" + hex(n.fill) + "), " + shape + ")";
      if (n.strokeW && n.stroke !== "none") mods += "\n      .border(" + n.strokeW + ".dp, Color(" + hex(n.stroke) + "), " + shape + ")";

      if (n.text === null || n.text === undefined) {
        k.push("  Box(");
        k.push("    " + mods.replace(/\n {6}/g, "\n      "));
        k.push("  )");
      } else {
        k.push("  Box(");
        k.push("    " + mods.replace(/\n {6}/g, "\n      ") + ",");
        k.push("    contentAlignment = Alignment." + (n.align === "center" ? "Center" : n.align === "right" ? "CenterEnd" : "CenterStart") + ",");
        k.push("  ) {");
        k.push("    Text(");
        k.push('      text = "' + String(n.text).replace(/\n/g, "\\n") + '",');
        k.push("      color = Color(" + hex(n.textColor) + "),");
        k.push("      fontSize = " + n.fontSize + ".sp,");
        k.push("      fontWeight = FontWeight." + (n.fontSize >= 22 ? "SemiBold" : "Normal") + ",");
        k.push("      textAlign = TextAlign." + (n.align === "center" ? "Center" : n.align === "right" ? "End" : "Start") + ",");
        k.push("      modifier = Modifier.padding(horizontal = 6.dp),");
        k.push("    )");
        k.push("  }");
      }
    });
    k.push("}");
    return k.join("\n");
  }

  function toCss(nodes, c) {
    var vis = nodes.filter(function (n) { return n.visible; });
    var s = [];
    s.push("/* GENERATED FROM contract " + c.checkpoint + " — DO NOT EDIT");
    s.push("   Same component keys and the same fractions as the Kotlin. */");
    s.push("");
    s.push(".screen {");
    s.push("  position: relative;");
    s.push("  width: 100%;");
    s.push("  aspect-ratio: " + W + " / " + H + ";");
    s.push("  overflow: hidden;");
    s.push("}");
    s.push("");
    s.push(".c { position: absolute; margin: 0; display: flex; align-items: center; overflow: hidden; }");
    s.push("");
    vis.forEach(function (n) {
      s.push("." + key(n) + " {");
      s.push("  left:   " + pct(n.x, W) + "%;");
      s.push("  top:    " + pct(n.y, H) + "%;");
      s.push("  width:  " + pct(n.w, W) + "%;");
      s.push("  height: " + pct(n.h, H) + "%;");
      if (n.fill !== "none") s.push("  background: " + n.fill + ";");
      if (n.strokeW && n.stroke !== "none") s.push("  border: " + n.strokeW + "px solid " + n.stroke + ";");
      s.push("  border-radius: " + (n.type === "ellipse" ? "50%" : n.radius + "px") + ";");
      if (n.opacity !== 1) s.push("  opacity: " + n.opacity + ";");
      if (n.text !== null && n.text !== undefined) {
        s.push("  color: " + n.textColor + ";");
        s.push("  font-size: " + n.fontSize + "px;");
        s.push("  font-weight: " + (n.fontSize >= 22 ? 600 : 400) + ";");
        s.push("  text-align: " + n.align + ";");
        s.push("  justify-content: " + (n.align === "center" ? "center" : n.align === "right" ? "flex-end" : "flex-start") + ";");
        s.push("  white-space: pre-wrap;");
        s.push("  padding: 0 6px;");
      }
      s.push("}");
      s.push("");
    });
    return s.join("\n");
  }

  function esc(s) {
    return String(s).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
  }

  function toHtml(nodes, c) {
    var vis = nodes.filter(function (n) { return n.visible; });
    var h = [];
    h.push("<!-- GENERATED FROM contract " + c.checkpoint + " — DO NOT EDIT -->");
    h.push('<link rel="stylesheet" href="home.generated.css">');
    h.push("");
    h.push('<section class="screen" data-screen="Home">');
    vis.forEach(function (n) {
      var cls = 'class="c ' + key(n) + '"';
      if (n.type === "image") { h.push("  <img " + cls + ' src="' + key(n) + '.png" alt="' + esc(n.text || "") + '">'); return; }
      if (n.text === null || n.text === undefined) { h.push("  <div " + cls + "></div>"); return; }
      h.push("  <div " + cls + ">" + esc(n.text) + "</div>");
    });
    h.push("</section>");
    return h.join("\n");
  }

  global.EditorCore = {
    W: W, H: H, PALETTE: PALETTE,
    seed: seed, make: make, node: node, byId: byId, nextId: nextId,
    applyBox: applyBox, applyText: applyText, transform: transform,
    contract: contract, toKotlin: toKotlin, toCss: toCss, toHtml: toHtml,
    key: key, pct: pct, frac: frac
  };
})(window);
