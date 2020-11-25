/* Copyright 2019 The TensorFlow Authors. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
==============================================================================*/

package org.tensorflow.lite.examples.detection.env;

//Importações
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Paint.Style;
import android.graphics.Rect;
import android.graphics.Typeface;
import java.util.Vector;

//---------------------------------------------------------------------------------------------------
/*Classe BorderedText
* Uma classe que encapsula as partes tediosas de renderização de texto legível com bordas em uma tela. */
public class BorderedText {

  //---------------------------------------------------------------------------------------------------
  //Atributos
  private final Paint interiorPaint;
  private final Paint exteriorPaint;
  private final float textSize;

  //---------------------------------------------------------------------------------------------------
  /**Métodos Construtores
   * Cria um objeto de texto com borda alinhado à esquerda com um interior branco e um exterior preto com
   * o tamanho do texto especificado.
   *
   * @param textSize tamanho do texto em pixels
   */
  public BorderedText(final float textSize) {
    this(Color.WHITE, Color.BLACK, textSize);
  }

  /**
   * Crie um objeto de texto com bordas com as cores internas e externas especificadas, tamanho do texto e
   * alinhamento.
   *
   * @param interiorColor cor do texto interior
   * @param exteriorColor cor do texto exterior
   * @param textSize tamanho do texto em pixels
   */
  public BorderedText(final int interiorColor, final int exteriorColor, final float textSize) {
    interiorPaint = new Paint();
    interiorPaint.setTextSize(textSize);
    interiorPaint.setColor(interiorColor);
    interiorPaint.setStyle(Style.FILL);
    interiorPaint.setAntiAlias(false);
    interiorPaint.setAlpha(255);

    exteriorPaint = new Paint();
    exteriorPaint.setTextSize(textSize);
    exteriorPaint.setColor(exteriorColor);
    exteriorPaint.setStyle(Style.FILL_AND_STROKE);
    exteriorPaint.setStrokeWidth(textSize / 8);
    exteriorPaint.setAntiAlias(false);
    exteriorPaint.setAlpha(255);

    this.textSize = textSize;
  }

  //---------------------------------------------------------------------------------------------------
  //Método setTypeface
  public void setTypeface(Typeface typeface) {
    interiorPaint.setTypeface(typeface);
    exteriorPaint.setTypeface(typeface);
  }

  //---------------------------------------------------------------------------------------------------
  //Método drawText
  public void drawText(final Canvas canvas, final float posX, final float posY, final String text) {
    canvas.drawText(text, posX, posY, exteriorPaint);
    canvas.drawText(text, posX, posY, interiorPaint);
  }

  //---------------------------------------------------------------------------------------------------
  //Método drawText
  public void drawText(
      final Canvas canvas, final float posX, final float posY, final String text, Paint bgPaint) {

    float width = exteriorPaint.measureText(text);
    float textSize = exteriorPaint.getTextSize();
    Paint paint = new Paint(bgPaint);
    paint.setStyle(Paint.Style.FILL);
    paint.setAlpha(160);
    canvas.drawRect(posX, (posY + (int) (textSize)), (posX + (int) (width)), posY, paint);

    canvas.drawText(text, posX, (posY + textSize), interiorPaint);
  }

  //---------------------------------------------------------------------------------------------------
  //Método drawLines
  public void drawLines(Canvas canvas, final float posX, final float posY, Vector<String> lines) {
    int lineNum = 0;
    for (final String line : lines) {
      drawText(canvas, posX, posY - getTextSize() * (lines.size() - lineNum - 1), line);
      ++lineNum;
    }
  }

  //---------------------------------------------------------------------------------------------------
  //Método setInteriorColor
  public void setInteriorColor(final int color) {
    interiorPaint.setColor(color);
  }

  //---------------------------------------------------------------------------------------------------
  //Método setExteriorColor
  public void setExteriorColor(final int color) {
    exteriorPaint.setColor(color);
  }

  //---------------------------------------------------------------------------------------------------
  //Método getTextSize
  public float getTextSize() {
    return textSize;
  }

  //---------------------------------------------------------------------------------------------------
  //Método setAlpha
  public void setAlpha(final int alpha) {
    interiorPaint.setAlpha(alpha);
    exteriorPaint.setAlpha(alpha);
  }

  //---------------------------------------------------------------------------------------------------
  //Método getTextBounds
  public void getTextBounds(
      final String line, final int index, final int count, final Rect lineBounds) {
    interiorPaint.getTextBounds(line, index, count, lineBounds);
  }

  //---------------------------------------------------------------------------------------------------
  //Método setTextAlign
  public void setTextAlign(final Align align) {
    interiorPaint.setTextAlign(align);
    exteriorPaint.setTextAlign(align);
  }

}//Fim da Classe
