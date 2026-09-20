import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import path from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";

import {
  ContractError,
  generateCss,
  generateKotlin,
  normalizeContract,
} from "./generate.mjs";

const HERE = path.dirname(fileURLToPath(import.meta.url));

async function exampleContract() {
  const source = await readFile(path.join(HERE, "examples", "home.json"), "utf8");
  return normalizeContract(JSON.parse(source));
}

test("generates stable Web CSS from the example contract", async () => {
  const contract = await exampleContract();
  const generated = generateCss(contract);
  const golden = await readFile(
    path.join(HERE, "generated", "web", "home.generated.css"),
    "utf8",
  );

  assert.equal(generated, golden);
  assert.match(generated, /box-sizing: border-box/);
  assert.match(generated, /\.rect_primaryAction \{[\s\S]*width: 87\.2%;/);
  assert.match(generated, /\.image_cover \{[\s\S]*object-fit: cover;/);
  assert.match(generated, /white-space: pre-wrap/);
});

test("generates stable Compose Kotlin from the same contract", async () => {
  const contract = await exampleContract();
  const generated = generateKotlin(contract);
  const golden = await readFile(
    path.join(HERE, "generated", "android", "HomeLayout.generated.kt"),
    "utf8",
  );

  assert.equal(generated, golden);
  assert.match(generated, /fun HomeLayoutGenerated/);
  assert.match(generated, /\.rel\(0\.064f, 0\.78f, 0\.872f, 0\.075f\)/);
  assert.match(generated, /\.alpha\(1f\)/);
  assert.match(generated, /private val IkkOvalShape = GenericShape/);
  assert.match(
    generated,
    /\.background\(Color\(0xFFFFFFFF\), RoundedCornerShape\(14\.dp\)\)\n\s+\.border\(1\.dp, Color\(0xFFDAD5E2\), RoundedCornerShape\(14\.dp\)\),\n\s+contentAlignment/,
  );
});

test("escapes Kotlin strings instead of emitting executable interpolation", () => {
  const contract = normalizeContract({
    schemaVersion: 1,
    checkpoint: "cp_escape",
    screen: "Escapes",
    reference: { w: 100, h: 100, unit: "dp" },
    layout: "relative",
    components: {
      text_1: {
        id: "n1",
        type: "text",
        rect: { x: 0, y: 0, w: 100, h: 20, unit: "%" },
        fill: "none",
        stroke: null,
        radius: 0,
        opacity: 1,
        text: {
          value: "Quote \" slash \\ dollar $value\nnext",
          size: 16,
          align: "start",
          color: "#000000"
        }
      }
    }
  });

  const generated = generateKotlin(contract);
  assert.match(generated, /Quote \\\" slash \\\\ dollar \\\$value\\nnext/);
});

test("rejects ambiguous or unsupported contract values", () => {
  assert.throws(
    () => normalizeContract({
      schemaVersion: 1,
      checkpoint: "cp_bad",
      screen: "Bad",
      reference: { w: 375, h: 667, unit: "px" },
      layout: "relative",
      components: {}
    }),
    (error) => error instanceof ContractError && /reference\.unit/.test(error.message),
  );

  assert.throws(
    () => normalizeContract({
      schemaVersion: 1,
      checkpoint: "cp_bad_visible",
      screen: "Bad",
      reference: { w: 375, h: 667, unit: "dp" },
      layout: "relative",
      components: {
        rect_header: {
          id: "n1",
          type: "rect",
          visible: "false",
          rect: { x: 0, y: 0, w: 100, h: 100, unit: "%" },
          fill: "#FFFFFF",
        },
      },
    }),
    (error) => error instanceof ContractError && /visible/.test(error.message),
  );
});
