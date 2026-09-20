#!/usr/bin/env node

import { promises as fs } from "node:fs";
import path from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

const CODEGEN_DIR = path.dirname(fileURLToPath(import.meta.url));
const DEFAULT_PATHS = {
  input: path.join(CODEGEN_DIR, "examples", "home.json"),
  css: path.join(CODEGEN_DIR, "generated", "web", "home.generated.css"),
  kotlin: path.join(CODEGEN_DIR, "generated", "android", "HomeLayout.generated.kt"),
};

const NODE_TYPES = new Set(["rect", "text", "ellipse", "image"]);
const ALIGNMENTS = new Set(["left", "center", "right", "start", "end"]);
const CONTENT_SCALES = new Set(["crop", "fit", "fill"]);
const HEX_COLOR = /^#[0-9a-fA-F]{6}$/;

export class ContractError extends Error {
  constructor(location, message) {
    super(`${location}: ${message}`);
    this.name = "ContractError";
  }
}

function fail(location, message) {
  throw new ContractError(location, message);
}

function objectAt(value, location) {
  if (!value || typeof value !== "object" || Array.isArray(value)) {
    fail(location, "expected an object");
  }
  return value;
}

function stringAt(value, location) {
  if (typeof value !== "string" || value.length === 0) {
    fail(location, "expected a non-empty string");
  }
  return value;
}

function numberAt(value, location) {
  if (typeof value !== "number" || !Number.isFinite(value)) {
    fail(location, "expected a finite number");
  }
  return value;
}

function booleanAt(value, location) {
  if (typeof value !== "boolean") {
    fail(location, "expected a boolean");
  }
  return value;
}

function positiveNumberAt(value, location) {
  const number = numberAt(value, location);
  if (number <= 0) fail(location, "expected a number greater than zero");
  return number;
}

function nonNegativeNumberAt(value, location) {
  const number = numberAt(value, location);
  if (number < 0) fail(location, "expected a non-negative number");
  return number;
}

function colorAt(value, location, { allowNone = false } = {}) {
  if (allowNone && (value === null || value === "none")) return null;
  if (typeof value !== "string" || !HEX_COLOR.test(value)) {
    fail(location, `expected #RRGGBB${allowNone ? ', "none", or null' : ""}`);
  }
  return value.toUpperCase();
}

function normalizeWeight(value, size, location) {
  if (value === undefined) return size >= 22 ? 600 : 400;
  const aliases = { normal: 400, medium: 500, semibold: 600 };
  const normalized = typeof value === "string" ? aliases[value.toLowerCase()] : value;
  if (![400, 500, 600].includes(normalized)) {
    fail(location, "expected 400, 500, 600, normal, medium, or semibold");
  }
  return normalized;
}

function normalizeText(value, location) {
  if (value === null || value === undefined) return null;
  const text = objectAt(value, location);
  const size = positiveNumberAt(text.size, `${location}.size`);
  const align = stringAt(text.align, `${location}.align`).toLowerCase();
  if (!ALIGNMENTS.has(align)) {
    fail(`${location}.align`, "expected left, center, right, start, or end");
  }

  let maxLines = null;
  if (text.maxLines !== null && text.maxLines !== undefined) {
    maxLines = numberAt(text.maxLines, `${location}.maxLines`);
    if (!Number.isInteger(maxLines) || maxLines <= 0) {
      fail(`${location}.maxLines`, "expected a positive integer or null");
    }
  }

  return {
    value: typeof text.value === "string"
      ? text.value
      : fail(`${location}.value`, "expected a string"),
    size,
    lineHeight: text.lineHeight === undefined
      ? round(size * 1.3, 3)
      : positiveNumberAt(text.lineHeight, `${location}.lineHeight`),
    weight: normalizeWeight(text.weight, size, `${location}.weight`),
    align,
    color: colorAt(text.color, `${location}.color`),
    fontFamily: text.fontFamily === undefined
      ? "Roboto"
      : stringAt(text.fontFamily, `${location}.fontFamily`),
    maxLines,
  };
}

function normalizeRadius(value, type, location) {
  if (value === undefined) return type === "ellipse" ? "50%" : 0;
  if (value === "50%") return value;
  return nonNegativeNumberAt(value, location);
}

function normalizeNode(key, value, inputOrder, seenIds, seenClasses) {
  const location = `components.${key}`;
  const node = objectAt(value, location);
  const id = stringAt(node.id, `${location}.id`);
  if (seenIds.has(id)) fail(`${location}.id`, `duplicate id ${JSON.stringify(id)}`);
  seenIds.add(id);

  const type = stringAt(node.type, `${location}.type`).toLowerCase();
  if (!NODE_TYPES.has(type)) {
    fail(`${location}.type`, `unsupported type ${JSON.stringify(type)}`);
  }

  const rect = objectAt(node.rect, `${location}.rect`);
  if (rect.unit !== "%") fail(`${location}.rect.unit`, 'only "%" is supported in contract v1');

  const cssClass = cssIdentifier(key);
  if (seenClasses.has(cssClass)) {
    fail(location, `component key collides with CSS class ${JSON.stringify(cssClass)}`);
  }
  seenClasses.add(cssClass);

  let stroke = null;
  if (node.stroke !== null && node.stroke !== undefined) {
    const rawStroke = objectAt(node.stroke, `${location}.stroke`);
    stroke = {
      color: colorAt(rawStroke.color, `${location}.stroke.color`),
      width: nonNegativeNumberAt(rawStroke.width, `${location}.stroke.width`),
    };
  }

  const opacity = node.opacity === undefined ? 1 : numberAt(node.opacity, `${location}.opacity`);
  if (opacity < 0 || opacity > 1) fail(`${location}.opacity`, "expected a value from 0 to 1");

  const z = node.z === undefined ? inputOrder : numberAt(node.z, `${location}.z`);
  if (!Number.isInteger(z)) fail(`${location}.z`, "expected an integer");

  const contentScale = node.contentScale === undefined
    ? "crop"
    : stringAt(node.contentScale, `${location}.contentScale`).toLowerCase();
  if (!CONTENT_SCALES.has(contentScale)) {
    fail(`${location}.contentScale`, "expected crop, fit, or fill");
  }

  return {
    key,
    cssClass,
    id,
    type,
    z,
    inputOrder,
    visible: node.visible === undefined ? true : booleanAt(node.visible, `${location}.visible`),
    rect: {
      x: numberAt(rect.x, `${location}.rect.x`),
      y: numberAt(rect.y, `${location}.rect.y`),
      w: positiveNumberAt(rect.w, `${location}.rect.w`),
      h: positiveNumberAt(rect.h, `${location}.rect.h`),
    },
    fill: colorAt(node.fill, `${location}.fill`, { allowNone: true }),
    stroke,
    radius: normalizeRadius(node.radius, type, `${location}.radius`),
    opacity,
    text: normalizeText(node.text, `${location}.text`),
    contentScale,
    alt: node.alt === undefined || node.alt === null
      ? null
      : stringAt(node.alt, `${location}.alt`),
  };
}

export function normalizeContract(value) {
  const contract = objectAt(value, "contract");
  // docs/json_contract.md §2 — the field is schemaVersion, and an unknown one
  // is rejected rather than guessed.
  if (contract.schemaVersion !== 1) {
    fail("contract.schemaVersion", "only schemaVersion 1 is supported");
  }
  const checkpoint = stringAt(contract.checkpoint, "contract.checkpoint");
  const screen = stringAt(contract.screen, "contract.screen");
  if (contract.layout !== "relative") fail("contract.layout", 'only "relative" is supported');

  const reference = objectAt(contract.reference, "contract.reference");
  if (reference.unit !== "dp") fail("contract.reference.unit", 'only "dp" is supported');

  const rawComponents = objectAt(contract.components, "contract.components");
  const seenIds = new Set();
  const seenClasses = new Set();
  const components = Object.entries(rawComponents).map(([key, node], index) =>
    normalizeNode(key, node, index, seenIds, seenClasses),
  );

  components.sort((a, b) => a.z - b.z || a.inputOrder - b.inputOrder || a.key.localeCompare(b.key));

  return {
    version: 1,
    checkpoint,
    screen,
    reference: {
      w: positiveNumberAt(reference.w, "contract.reference.w"),
      h: positiveNumberAt(reference.h, "contract.reference.h"),
    },
    layout: "relative",
    components,
  };
}

function round(value, precision) {
  const factor = 10 ** precision;
  return Math.round(value * factor) / factor;
}

function numberText(value, precision = 4) {
  if (Number.isInteger(value)) return String(value);
  return value.toFixed(precision).replace(/0+$/, "").replace(/\.$/, "");
}

function cssIdentifier(value) {
  let result = String(value).replace(/[^a-zA-Z0-9_-]/g, "-");
  if (!/^[a-zA-Z_-]/.test(result)) result = `node-${result}`;
  return result;
}

function cssString(value) {
  return `"${String(value).replace(/\\/g, "\\\\").replace(/"/g, '\\"')}"`;
}

function cssAlignment(align) {
  if (align === "center") return { text: "center", content: "center" };
  if (align === "right" || align === "end") return { text: "end", content: "flex-end" };
  return { text: "start", content: "flex-start" };
}

function cssRadius(node) {
  if (node.type === "ellipse" || node.radius === "50%") return "50%";
  return `${numberText(node.radius)}px`;
}

export function generateCss(contract) {
  const lines = [
    `/* GENERATED FROM contract ${contract.checkpoint} — DO NOT EDIT. */`,
    "",
    ".screen {",
    "  position: relative;",
    "  width: 100%;",
    `  aspect-ratio: ${numberText(contract.reference.w)} / ${numberText(contract.reference.h)};`,
    "  overflow: hidden;",
    "}",
    "",
    ".ikk-node {",
    "  position: absolute;",
    "  box-sizing: border-box;",
    "  margin: 0;",
    "  display: flex;",
    "  align-items: center;",
    "  overflow: hidden;",
    "}",
    "",
  ];

  for (const node of contract.components) {
    const alignment = node.text ? cssAlignment(node.text.align) : null;
    lines.push(`.${node.cssClass} {`);
    lines.push(`  left: ${numberText(node.rect.x)}%;`);
    lines.push(`  top: ${numberText(node.rect.y)}%;`);
    lines.push(`  width: ${numberText(node.rect.w)}%;`);
    lines.push(`  height: ${numberText(node.rect.h)}%;`);
    lines.push(`  z-index: ${node.z};`);
    if (!node.visible) lines.push("  display: none;");
    if (node.fill) lines.push(`  background: ${node.fill};`);
    if (node.stroke && node.stroke.width > 0) {
      lines.push(`  border: ${numberText(node.stroke.width)}px solid ${node.stroke.color};`);
    }
    lines.push(`  border-radius: ${cssRadius(node)};`);
    if (node.opacity !== 1) lines.push(`  opacity: ${numberText(node.opacity)};`);
    if (node.type === "image") {
      const objectFit = node.contentScale === "fill" ? "fill" : node.contentScale === "fit" ? "contain" : "cover";
      lines.push(`  object-fit: ${objectFit};`);
    }
    if (node.text) {
      lines.push(`  color: ${node.text.color};`);
      lines.push(`  font-family: ${cssString(node.text.fontFamily)}, sans-serif;`);
      lines.push(`  font-size: ${numberText(node.text.size)}px;`);
      lines.push(`  line-height: ${numberText(node.text.lineHeight)}px;`);
      lines.push(`  font-weight: ${node.text.weight};`);
      lines.push(`  text-align: ${alignment.text};`);
      lines.push(`  justify-content: ${alignment.content};`);
      lines.push("  white-space: pre-wrap;");
      lines.push("  padding: 0 6px;");
    }
    lines.push("}", "");
  }

  return `${lines.join("\n").trimEnd()}\n`;
}

function kotlinString(value) {
  let result = "";
  for (const character of String(value)) {
    const code = character.charCodeAt(0);
    if (character === "\\") result += "\\\\";
    else if (character === '"') result += '\\"';
    else if (character === "$") result += "\\$";
    else if (character === "\n") result += "\\n";
    else if (character === "\r") result += "\\r";
    else if (character === "\t") result += "\\t";
    else if (code < 0x20) result += `\\u${code.toString(16).padStart(4, "0")}`;
    else result += character;
  }
  return `"${result}"`;
}

function kotlinFloat(value, precision = 4) {
  return `${numberText(value, precision)}f`;
}

function kotlinUnit(value, unit) {
  return Number.isInteger(value)
    ? `${value}.${unit}`
    : `${numberText(value)}f.${unit}`;
}

function kotlinColor(color) {
  return `Color(0xFF${color.slice(1)})`;
}

function kotlinShape(node) {
  if (node.type === "ellipse" || node.radius === "50%") return "IkkOvalShape";
  return `RoundedCornerShape(${kotlinUnit(node.radius, "dp")})`;
}

function kotlinAlignment(align) {
  if (align === "center") return { box: "Alignment.Center", text: "TextAlign.Center" };
  if (align === "right" || align === "end") return { box: "Alignment.CenterEnd", text: "TextAlign.End" };
  return { box: "Alignment.CenterStart", text: "TextAlign.Start" };
}

function kotlinWeight(weight) {
  if (weight === 600) return "FontWeight.SemiBold";
  if (weight === 500) return "FontWeight.Medium";
  return "FontWeight.Normal";
}

function kotlinContentScale(contentScale) {
  if (contentScale === "fill") return "ContentScale.FillBounds";
  if (contentScale === "fit") return "ContentScale.Fit";
  return "ContentScale.Crop";
}

function kotlinName(value) {
  const pieces = String(value).split(/[^a-zA-Z0-9]+/).filter(Boolean);
  let result = pieces.map((piece) => piece[0].toUpperCase() + piece.slice(1)).join("") || "Screen";
  if (/^[0-9]/.test(result)) result = `Screen${result}`;
  return result;
}

function kotlinModifierLines(node) {
  const lines = [
    "Modifier",
    `  .rel(${kotlinFloat(node.rect.x / 100)}, ${kotlinFloat(node.rect.y / 100)}, ${kotlinFloat(node.rect.w / 100)}, ${kotlinFloat(node.rect.h / 100)})`,
    `  .zIndex(${kotlinFloat(node.z)})`,
    `  .alpha(${kotlinFloat(node.opacity)})`,
  ];
  const shape = kotlinShape(node);
  if (node.type === "image") lines.push(`  .clip(${shape})`);
  if (node.fill) lines.push(`  .background(${kotlinColor(node.fill)}, ${shape})`);
  if (node.stroke && node.stroke.width > 0) {
    lines.push(`  .border(${kotlinUnit(node.stroke.width, "dp")}, ${kotlinColor(node.stroke.color)}, ${shape})`);
  }
  return lines;
}

function indent(lines, spaces) {
  const prefix = " ".repeat(spaces);
  return lines.map((line) => `${prefix}${line}`);
}

export function generateKotlin(contract) {
  const visibleNodes = contract.components.filter((node) => node.visible);
  const hasEllipse = visibleNodes.some((node) => node.type === "ellipse" || node.radius === "50%");
  const hasImage = visibleNodes.some((node) => node.type === "image");
  const functionName = `${kotlinName(contract.screen)}LayoutGenerated`;
  const lines = [
    `// GENERATED FROM contract ${contract.checkpoint} — DO NOT EDIT.`,
    "package com.ikk.ui.generated",
    "",
    "import androidx.compose.foundation.background",
    "import androidx.compose.foundation.border",
    "import androidx.compose.foundation.layout.Box",
    "import androidx.compose.foundation.layout.BoxWithConstraints",
    "import androidx.compose.foundation.layout.aspectRatio",
    "import androidx.compose.foundation.layout.offset",
    "import androidx.compose.foundation.layout.padding",
    "import androidx.compose.foundation.layout.size",
    ...(hasEllipse ? ["import androidx.compose.foundation.shape.GenericShape"] : []),
    "import androidx.compose.foundation.shape.RoundedCornerShape",
    "import androidx.compose.material3.Text",
    "import androidx.compose.runtime.Composable",
    "import androidx.compose.ui.Alignment",
    "import androidx.compose.ui.Modifier",
    "import androidx.compose.ui.draw.alpha",
    ...(hasImage ? ["import androidx.compose.ui.draw.clip"] : []),
    ...(hasEllipse ? ["import androidx.compose.ui.geometry.Rect"] : []),
    "import androidx.compose.ui.graphics.Color",
    "import androidx.compose.ui.layout.ContentScale",
    "import androidx.compose.ui.text.font.FontFamily",
    "import androidx.compose.ui.text.font.FontWeight",
    "import androidx.compose.ui.text.style.TextAlign",
    "import androidx.compose.ui.unit.dp",
    "import androidx.compose.ui.unit.sp",
    "import androidx.compose.ui.zIndex",
    "",
  ];

  if (hasEllipse) {
    lines.push(
      "private val IkkOvalShape = GenericShape { size, _ ->",
      "    addOval(Rect(0f, 0f, size.width, size.height))",
      "}",
      "",
    );
  }

  lines.push("@Composable", `fun ${functionName}(`, "    modifier: Modifier = Modifier,");
  if (hasImage) {
    lines.push(
      "    imageContent: @Composable (",
      "        id: String,",
      "        contentDescription: String?,",
      "        contentScale: ContentScale,",
      "        modifier: Modifier,",
      "    ) -> Unit = { _, _, _, imageModifier -> Box(imageModifier) },",
    );
  }
  lines.push(
    ") {",
    "    BoxWithConstraints(",
    `        modifier = modifier.aspectRatio(${kotlinFloat(contract.reference.w)} / ${kotlinFloat(contract.reference.h)}),`,
    "    ) {",
    "        fun Modifier.rel(x: Float, y: Float, w: Float, h: Float) = this",
    "            .offset(x = maxWidth * x, y = maxHeight * y)",
    "            .size(width = maxWidth * w, height = maxHeight * h)",
    "",
  );

  for (const node of visibleNodes) {
    const modifierLines = kotlinModifierLines(node);
    modifierLines[modifierLines.length - 1] += ",";
    if (node.type === "image") {
      lines.push(
        "        imageContent(",
        `            id = ${kotlinString(node.id)},`,
        `            contentDescription = ${node.alt === null ? "null" : kotlinString(node.alt)},`,
        `            contentScale = ${kotlinContentScale(node.contentScale)},`,
        "            modifier =",
        ...indent(modifierLines, 16),
        "        )",
        "",
      );
      continue;
    }

    if (!node.text) {
      lines.push(
        "        Box(",
        "            modifier =",
        ...indent(modifierLines, 16),
        "        )",
        "",
      );
      continue;
    }

    const alignment = kotlinAlignment(node.text.align);
    lines.push(
      "        Box(",
      "            modifier =",
      ...indent(modifierLines, 16),
      `            contentAlignment = ${alignment.box},`,
      "        ) {",
      "            Text(",
      `                text = ${kotlinString(node.text.value)},`,
      `                color = ${kotlinColor(node.text.color)},`,
      "                fontFamily = FontFamily.SansSerif,",
      `                fontSize = ${kotlinUnit(node.text.size, "sp")},`,
      `                lineHeight = ${kotlinUnit(node.text.lineHeight, "sp")},`,
      `                fontWeight = ${kotlinWeight(node.text.weight)},`,
      `                textAlign = ${alignment.text},`,
      ...(node.text.maxLines === null ? [] : [`                maxLines = ${node.text.maxLines},`]),
      "                modifier = Modifier.padding(horizontal = 6.dp),",
      "            )",
      "        }",
      "",
    );
  }

  lines.push("    }", "}");
  return `${lines.join("\n").trimEnd()}\n`;
}

function parseArguments(argv) {
  const options = { ...DEFAULT_PATHS, check: false };
  for (let index = 0; index < argv.length; index += 1) {
    const argument = argv[index];
    if (argument === "--check") {
      options.check = true;
      continue;
    }
    if (["--input", "--css", "--kotlin"].includes(argument)) {
      const value = argv[index + 1];
      if (!value) fail("arguments", `${argument} requires a path`);
      options[argument.slice(2)] = path.resolve(value);
      index += 1;
      continue;
    }
    fail("arguments", `unknown option ${JSON.stringify(argument)}`);
  }
  return options;
}

async function writeOrCheck(outputPath, content, check) {
  if (check) {
    let existing;
    try {
      existing = await fs.readFile(outputPath, "utf8");
    } catch (error) {
      if (error.code === "ENOENT") fail(outputPath, "generated file is missing");
      throw error;
    }
    if (existing !== content) fail(outputPath, "generated file is stale; run npm run generate");
    return;
  }

  await fs.mkdir(path.dirname(outputPath), { recursive: true });
  await fs.writeFile(outputPath, content, "utf8");
}

export async function runCodegen(options) {
  const raw = await fs.readFile(options.input, "utf8");
  let parsed;
  try {
    parsed = JSON.parse(raw);
  } catch (error) {
    fail(options.input, `invalid JSON: ${error.message}`);
  }

  const contract = normalizeContract(parsed);
  const css = generateCss(contract);
  const kotlin = generateKotlin(contract);
  await Promise.all([
    writeOrCheck(options.css, css, options.check),
    writeOrCheck(options.kotlin, kotlin, options.check),
  ]);
  return { contract, css, kotlin };
}

async function main() {
  const options = parseArguments(process.argv.slice(2));
  const result = await runCodegen(options);
  const mode = options.check ? "verified" : "generated";
  process.stdout.write(
    `${mode} ${result.contract.components.length} components from ${path.relative(process.cwd(), options.input)}\n` +
    `css: ${path.relative(process.cwd(), options.css)}\n` +
    `kotlin: ${path.relative(process.cwd(), options.kotlin)}\n`,
  );
}

const entryPoint = process.argv[1]
  ? pathToFileURL(path.resolve(process.argv[1])).href
  : null;

if (entryPoint === import.meta.url) {
  main().catch((error) => {
    process.stderr.write(`${error.name}: ${error.message}\n`);
    process.exitCode = 1;
  });
}
