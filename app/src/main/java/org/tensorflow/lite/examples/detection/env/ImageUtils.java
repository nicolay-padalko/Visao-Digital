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

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.os.Environment;
import java.io.File;
import java.io.FileOutputStream;

//---------------------------------------------------------------------------------------------------
/** Classe utilitária para manipulação de imagens. */
public class ImageUtils {

  //Atributos
  // Este valor é 2 ^ 18 - 1 e é usado para fixar os valores RGB antes de seus intervalos
  // são normalizados para oito bits.
  static final int kMaxChannelValue = 262143;

  @SuppressWarnings("unused")
  private static final Logger LOGGER = new Logger();

  //---------------------------------------------------------------------------------------------------
  /** Método getYUVByteSize
   * Método utilitário para calcular o tamanho alocado em bytes de uma imagem YUV420SP do dado
   * dimensões.
   */
  public static int getYUVByteSize(final int width, final int height) {
    // O plano de luminância requer 1 byte por pixel.
    final int ySize = width * height;

    // O plano UV funciona em blocos 2x2, então dimensões com tamanhos ímpares devem ser arredondadas para cima.
    // Cada bloco 2x2 leva 2 bytes para codificar, um para cada U e V.
    final int uvSize = ((width + 1) / 2) * ((height + 1) / 2) * 2;

    return ySize + uvSize;
  }

  //---------------------------------------------------------------------------------------------------
  /**Método saveBitmap
   * Salva um objeto Bitmap em disco para análise.
   *
   * @param bitmap O bitmap a ser salvo
   */
  public static void saveBitmap(final Bitmap bitmap) {
    saveBitmap(bitmap, "preview.png");
  }

  //---------------------------------------------------------------------------------------------------
  /**Método saveBitmap
   * Salva um objeto Bitmap em disco para análise.
   *
   * @param bitmap O Bitmap a ser salvo
   * @param filename A localização para salvar o Bitmap
   */
  public static void saveBitmap(final Bitmap bitmap, final String filename) {
    final String root =
        Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + "tensorflow";
    LOGGER.i("Saving %dx%d bitmap to %s.", bitmap.getWidth(), bitmap.getHeight(), root);
    final File myDir = new File(root);

    if (!myDir.mkdirs()) {
      LOGGER.i("Make dir failed");
    }

    final String fname = filename;
    final File file = new File(myDir, fname);
    if (file.exists()) {
      file.delete();
    }
    try {
      final FileOutputStream out = new FileOutputStream(file);
      bitmap.compress(Bitmap.CompressFormat.PNG, 99, out);
      out.flush();
      out.close();
    } catch (final Exception e) {
      LOGGER.e(e, "Exception!");
    }
  }

  //---------------------------------------------------------------------------------------------------
  //Método convertYUV420SPToARGB8888
  public static void convertYUV420SPToARGB8888(byte[] input, int width, int height, int[] output) {
    final int frameSize = width * height;
    for (int j = 0, yp = 0; j < height; j++) {
      int uvp = frameSize + (j >> 1) * width;
      int u = 0;
      int v = 0;

      for (int i = 0; i < width; i++, yp++) {
        int y = 0xff & input[yp];
        if ((i & 1) == 0) {
          v = 0xff & input[uvp++];
          u = 0xff & input[uvp++];
        }

        output[yp] = YUV2RGB(y, u, v);
      }
    }
  }

  //---------------------------------------------------------------------------------------------------
  //Método YUV2RGB
  private static int YUV2RGB(int y, int u, int v) {
    // Ajusta e checa os valores YUV
    y = (y - 16) < 0 ? 0 : (y - 16);
    u -= 128;
    v -= 128;

    // Este é o equivalente em ponto flutuante. Fazemos a conversão em inteiro
    // porque alguns dispositivos Android não possuem ponto flutuante no hardware.
    // nR = (int) (1,164 * nY + 2,018 * nU);
    // nG = (int) (1,164 * nY - 0,813 * nV - 0,391 * nU);
    // nB = (int) (1,164 * nY + 1,596 * nV);
    int y1192 = 1192 * y;
    int r = (y1192 + 1634 * v);
    int g = (y1192 - 833 * v - 400 * u);
    int b = (y1192 + 2066 * u);

    // Recorte dos valores RGB para dentro dos limites [0, kMaxChannelValue]
    r = r > kMaxChannelValue ? kMaxChannelValue : (r < 0 ? 0 : r);
    g = g > kMaxChannelValue ? kMaxChannelValue : (g < 0 ? 0 : g);
    b = b > kMaxChannelValue ? kMaxChannelValue : (b < 0 ? 0 : b);

    return 0xff000000 | ((r << 6) & 0xff0000) | ((g >> 2) & 0xff00) | ((b >> 10) & 0xff);
  }

  //---------------------------------------------------------------------------------------------------
  //Método convertYUV420ToARGB8888
  public static void convertYUV420ToARGB8888(
      byte[] yData,
      byte[] uData,
      byte[] vData,
      int width,
      int height,
      int yRowStride,
      int uvRowStride,
      int uvPixelStride,
      int[] out) {
    int yp = 0;
    for (int j = 0; j < height; j++) {
      int pY = yRowStride * j;
      int pUV = uvRowStride * (j >> 1);

      for (int i = 0; i < width; i++) {
        int uv_offset = pUV + (i >> 1) * uvPixelStride;

        out[yp++] = YUV2RGB(0xff & yData[pY + i], 0xff & uData[uv_offset], 0xff & vData[uv_offset]);
      }
    }
  }

  //---------------------------------------------------------------------------------------------------
  /**Método Matrix
   * Retorna uma matriz de transformação de um quadro de referência para outro. Lida com corte (se
   * manter a proporção do aspecto é desejado) e rotação.
   *
   * @param srcWidth Largura do quadro de origem.
   * @param srcHeight Altura do quadro de origem.
   * @param dstWidth Largura do quadro de destino.
   * @param dstHeight Altura do quadro de destino.
   * @param applyRotation Quantidade de rotação a ser aplicada de um quadro a outro. Deve ser um múltiplo
   * * de 90.
   * @param maintainAspectRatio Se verdadeiro, irá garantir que a escala em x e y permaneça constante,
   *     * recortar a imagem, se necessário.
   * @return A transformação cumprindo os requisitos desejados.
   */
  public static Matrix getTransformationMatrix(
      final int srcWidth,
      final int srcHeight,
      final int dstWidth,
      final int dstHeight,
      final int applyRotation,
      final boolean maintainAspectRatio) {
    final Matrix matrix = new Matrix();

    if (applyRotation != 0) {
      if (applyRotation % 90 != 0) {
        LOGGER.w("Rotation of %d % 90 != 0", applyRotation);
      }

      // Traduzir para que o centro da imagem esteja na origem.
      matrix.postTranslate(-srcWidth / 2.0f, -srcHeight / 2.0f);

      // Roda ao redor da origem
      matrix.postRotate(applyRotation);
    }

    // Considere a rotação já aplicada, se houver, e determine como
    // muita escala é necessária para cada eixo.
    final boolean transpose = (Math.abs(applyRotation) + 90) % 180 == 0;

    final int inWidth = transpose ? srcHeight : srcWidth;
    final int inHeight = transpose ? srcWidth : srcHeight;

    // Aplique escala, se necessário.
    if (inWidth != dstWidth || inHeight != dstHeight) {
      final float scaleFactorX = dstWidth / (float) inWidth;
      final float scaleFactorY = dstHeight / (float) inHeight;

      if (maintainAspectRatio) {
        // Escala pelo fator mínimo para que dst seja preenchido completamente enquanto
        // mantendo a proporção do aspecto. Algumas imagens podem cair da borda.
        final float scaleFactor = Math.max(scaleFactorX, scaleFactorY);
        matrix.postScale(scaleFactor, scaleFactor);
      } else {
        // Escale exatamente para preencher dst de src.
        matrix.postScale(scaleFactorX, scaleFactorY);
      }
    }

    if (applyRotation != 0) {
      // Traduzir de volta da referência centrada na origem para o quadro de destino.
      matrix.postTranslate(dstWidth / 2.0f, dstHeight / 2.0f);
    }

    return matrix;
  }

}//Fim da classe
