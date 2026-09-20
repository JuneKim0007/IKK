
## json format

{
  "version": 1,
  "checkpoint": "cp_005",
  "screen": "Home",
  "reference": {
    "w": 375,
    "h": 667,
    "unit": "dp"
  },
  "layout": "relative",
  "components": {
    "rect_1": {
      "id": "n1",
      "type": "rect",
      "rect": {
        "x": 0,
        "y": 0,
        "w": 100,
        "h": 27,
        "unit": "%"
      },
      "fill": "#65558F",
      "stroke": null,
      "radius": 0,
      "opacity": 1,
      "text": null,
      "updatedAt": null
    },
    "text_2": {
      "id": "n2",
      "type": "text",
      "rect": {

## example generated css file

* GENERATED FROM contract cp_005 — DO NOT EDIT
   Same component keys and the same fractions as the Kotlin. */

.screen {
  position: relative;
  width: 100%;
  aspect-ratio: 375 / 667;
  overflow: hidden;
}

.c { position: absolute; margin: 0; display: flex; align-items: center; overflow: hidden; }

.rect_1 {
  left:   0%;
  top:    0%;
  width:  100%;
  height: 27%;
  background: #65558F;
  border-radius: 0px;
}

.text_2 {
  left:   7.5%;
  top:    11.1%;
  width:  69.3%;
  height: 5.1%;
  border-radius: 0px;
  color: #FFFFFF;
  font-size: 26px;
  font-weight: 600;
  text-align: left;
  justify-content: flex-start;
# example generated kt file

// GENERATED FROM contract cp_003 — DO NOT EDIT
// Rewritten wholesale on every [Generate]. Behaviour goes in Home.kt

package com.ikk.ui.generated

@Composable
fun HomeLayout(modifier: Modifier = Modifier) = BoxWithConstraints(modifier.fillMaxSize()) {
  // Every rect is a fraction of this box, so one contract lays out at any size.
  fun Modifier.rel(x: Float, y: Float, w: Float, h: Float) = this
    .offset(maxWidth * x, maxHeight * y)
    .size(maxWidth * w, maxHeight * h)

  Box(
    Modifier.rel(0.000f, 0.000f, 1.000f, 0.270f)
      .background(Color(0xFF65558F), RoundedCornerShape(0.dp))
  )
  Box(
    Modifier.rel(0.075f, 0.111f, 0.693f, 0.051f),
    contentAlignment = Alignment.CenterStart,
  ) {
    Text(
      text = "Good morning",
      color = Color(0xFFFFFFFF),
      fontSize = 26.sp,
      fontWeight = FontWeight.SemiBold,
      textAlign = TextAlign.Start,
      modifier = Modifier.padding(horizontal = 6.dp),
    )
  }
  Box(
    Modifier.rel(0.075f, 0.168f, 0.747f, 0.033f),
    contentAlignment = Alignment.CenterStart,
  )
