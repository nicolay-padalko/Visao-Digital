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

package org.tensorflow.lite.examples.detection.tracking;

//Importações
import android.app.Activity;
import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Cap;
import android.graphics.Paint.Join;
import android.graphics.Paint.Style;
import android.graphics.RectF;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;
import android.util.TypedValue;
import androidx.annotation.RequiresApi;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import org.tensorflow.lite.examples.detection.Utilities;
import org.tensorflow.lite.examples.detection.env.BorderedText;
import org.tensorflow.lite.examples.detection.env.ImageUtils;
import org.tensorflow.lite.examples.detection.env.Logger;
import org.tensorflow.lite.examples.detection.tflite.Detector.Recognition;

//--------------------------------------------------------------------------------------------------------
/*Classe MultiBoxTracker
* Um rastreador que lida com supressão não máxima e combina objetos existentes com novas detecções.
* É nesta classe que são implemetados os métodos usados para traduzir os resultados da detecção para
* o português e, chamado o método que vai converter o texto da detecção em uma mensagem de aúdio */
public class MultiBoxTracker extends Activity {

  //--------------------------------------------------------------------------------------------------------
  //Atributos
  private static final float TEXT_SIZE_DIP = 18;
  private static final float MIN_SIZE = 16.0f;
  private static final int[] COLORS = {
    Color.BLUE,
    Color.RED,
    Color.GREEN,
    Color.YELLOW,
    Color.CYAN,
    Color.MAGENTA,
    Color.WHITE,
    Color.parseColor("#55FF55"),
    Color.parseColor("#FFA500"),
    Color.parseColor("#FF8888"),
    Color.parseColor("#AAAAFF"),
    Color.parseColor("#FFFFAA"),
    Color.parseColor("#55AAAA"),
    Color.parseColor("#AA33AA"),
    Color.parseColor("#0D0068")
  };
  final List<Pair<Float, RectF>> screenRects = new LinkedList<Pair<Float, RectF>>();
  private final Logger logger = new Logger();
  private final Queue<Integer> availableColors = new LinkedList<Integer>();
  private final List<TrackedRecognition> trackedObjects = new LinkedList<TrackedRecognition>();
  private final Paint boxPaint = new Paint();
  private final float textSizePx;
  private final BorderedText borderedText;
  private Matrix frameToCanvasMatrix;
  private int frameWidth;
  private int frameHeight;
  private int sensorOrientation;

  //---------------------------------------------------------------------------------------------------
  //Método Construtor
  public MultiBoxTracker(final Context context) {
    for (final int color : COLORS) {
      availableColors.add(color);
    }

    boxPaint.setColor(Color.RED);
    boxPaint.setStyle(Style.STROKE);
    boxPaint.setStrokeWidth(10.0f);
    boxPaint.setStrokeCap(Cap.ROUND);
    boxPaint.setStrokeJoin(Join.ROUND);
    boxPaint.setStrokeMiter(100);

    textSizePx =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP, context.getResources().getDisplayMetrics());
    borderedText = new BorderedText(textSizePx);
  }

  //--------------------------------------------------------------------------------------------------------
  //Método setFrameConfiguration
  //Configurações da tela
  public synchronized void setFrameConfiguration(
      final int width, final int height, final int sensorOrientation) {
    frameWidth = width;
    frameHeight = height;
    this.sensorOrientation = sensorOrientation;
  }

  //--------------------------------------------------------------------------------------------------------
  //Método drawDebug
  public synchronized void drawDebug(final Canvas canvas) {
    final Paint textPaint = new Paint();
    textPaint.setColor(Color.WHITE);
    textPaint.setTextSize(60.0f);

    final Paint boxPaint = new Paint();
    boxPaint.setColor(Color.RED);
    boxPaint.setAlpha(200);
    boxPaint.setStyle(Style.STROKE);

    for (final Pair<Float, RectF> detection : screenRects) {
      final RectF rect = detection.second;
      canvas.drawRect(rect, boxPaint);
      canvas.drawText("" + detection.first, rect.left, rect.top, textPaint);
      borderedText.drawText(canvas, rect.centerX(), rect.centerY(), "" + detection.first);
    }
  }

  //--------------------------------------------------------------------------------------------------------
  //Método trackResults
  public synchronized void trackResults(final List<Recognition> results, final long timestamp) {
    logger.i("Processing %d results from %d", results.size(), timestamp);
    processResults(results);
  }

  //--------------------------------------------------------------------------------------------------------
  //Método getFrameToCanvasMatrix
  private Matrix getFrameToCanvasMatrix() {
    return frameToCanvasMatrix;
  }

  //--------------------------------------------------------------------------------------------------------
  //Método draw
  public synchronized void draw(final Canvas canvas) {
    final boolean rotated = sensorOrientation % 180 == 90;
    final float multiplier =
        Math.min(
            canvas.getHeight() / (float) (rotated ? frameWidth : frameHeight),
            canvas.getWidth() / (float) (rotated ? frameHeight : frameWidth));
    frameToCanvasMatrix =
        ImageUtils.getTransformationMatrix(
            frameWidth,
            frameHeight,
            (int) (multiplier * (rotated ? frameHeight : frameWidth)),
            (int) (multiplier * (rotated ? frameWidth : frameHeight)),
            sensorOrientation,
            false);
    for (final TrackedRecognition recognition : trackedObjects) {

      //Objeto para manipular retângulo
      final RectF trackedPos = new RectF(recognition.location);


      getFrameToCanvasMatrix().mapRect(trackedPos);
      boxPaint.setColor(recognition.color);

      float cornerSize = Math.min(trackedPos.width(), trackedPos.height()) / 8.0f;
      canvas.drawRoundRect(trackedPos, cornerSize, cornerSize, boxPaint);

      final String labelString =
          !TextUtils.isEmpty(recognition.title)
              ? String.format("%s %.2f", recognition.title, (100 * recognition.detectionConfidence))
              : String.format("%.2f", (100 * recognition.detectionConfidence));

      borderedText.drawText(
          canvas, trackedPos.left + cornerSize, trackedPos.top, labelString + "%", boxPaint);
    }
  }
 //------------------------------------------------------------------------------------------------------
  //Método teste
  //Método que combina outros métodos para emitir a resposta auditiva
  @RequiresApi(api = Build.VERSION_CODES.O)
  public void teste(Context context){

    int cont = 0;
    String texto = "nada";
    for (final TrackedRecognition recognition : trackedObjects) {
      final RectF trackedPos = new RectF(recognition.location);

      leitura(context);
      cont++;
      texto=recognition.title;
      addVetor(context,texto);

      boolean b = verificacao(context,texto);
      if(b == true){

        String auxiliar = criaTranslate2(texto,context);
        String t = auxiliar + " a frente";
        new Utilities(context, t);
        Log.d("o objeto pode ser","dito");

      }else{

        Log.d("o objeto não pode ser","dito");}
      break;

      }

  }
  //---------------------------------------------------------------------------------------------------
  //Método verificacao
  //Método que verifica, no arquivo de texto vetores.txt, os objetos chamados e a quantidade de chamadas
  //dos mesmos
  public boolean verificacao(Context context, String text){

    boolean cond = false;
    boolean cond2 = false;
    String[] t = leVetores(context,text);
    int s = somaQ(context);
    int l = contaLinha(context);

    if(s<30 && l<6){
      cond2 = true;
    }
    else if(s<30){cond2 = true;}
    else{cond2 = false;}

    if(cond2 == true) {
      if (t[1] != null) {

        int n = 0;
        n = Integer.parseInt(t[1]);

        switch (n) {

          case 0:
            Log.d("verif", "ok");
            break;
          case 1:
            cond = true;
            break;
          default:

            cond = false;
        }
      }
    }else{gravar(context);}
    return cond;
  }
  //------------------------------------------------------------------------------------------------
  //Método somaQ
  //Este método retorna a soma das chamadas dos objetos armazenados em vetores.txt
  public int somaQ(Context context){

    int nn = 0;
    int num1 = contaLinha(context);
    String[] vetor = new String[num1];

    try{
      File arquivo = new File(context.getFilesDir(), "vetores.txt");
      FileReader fileReader = new FileReader(arquivo);
      BufferedReader reader = new BufferedReader(fileReader);
      vetor = preencheStr(context);
      String linha;
      String a;
      int ii = 0;
      while(vetor[ii]!=null){

        linha = vetor[ii];
        a = linha.substring(linha.indexOf('-') + 1);
        nn = nn + Integer.parseInt(a);
        ii++;

      }
      fileReader.close();
      reader.close();
    }catch(Exception e){
      e.printStackTrace();
    }
    return nn;
  }
  //------------------------------------------------------------------------------------------------
  //Método gravar
  //Este método apaga todo o conteúdo de dentro do arquivo vetores.txt
  public void gravar(Context context){

    try{

      File arquivo = new File(context.getFilesDir(), "vetores.txt");
      FileOutputStream output = new FileOutputStream(arquivo, true);
      FileOutputStream output2 = new FileOutputStream(arquivo, false);
      OutputStreamWriter outputWriter = new OutputStreamWriter(output);
      OutputStreamWriter outputWriter2 = new OutputStreamWriter(output2);
      FileReader fileReader = new FileReader(arquivo);
      BufferedReader reader = new BufferedReader(fileReader);
      String linha;

      outputWriter2.write("");
      outputWriter2.close();
      fileReader.close();
      reader.close();
    }
    catch(Exception e){e.printStackTrace();}
  }
  //-------------------------------------------------------------------------------------------------
//Método leitura
  //Este método apresenta a leitura de cada linha do arquivo vetores.txt
    public void leitura(Context context){

        try{

            File arquivo = new File(context.getFilesDir(), "vetores.txt");
            FileReader fileReader = new FileReader(arquivo);
            BufferedReader reader = new BufferedReader(fileReader);
            String linha;
            while((linha = reader.readLine())!=null){

                Log.d("leitura",linha);
            }
            reader.close();
            fileReader.close();
        }
        catch(Exception e){e.printStackTrace();}

    }
  //---------------------------------------------------------------------------------------------------------
  //Método leVetores
  //Este método procura o texto passado como parâmetro dentro do arquivo vetores.txt e retorna um vetor
  //Com a String do texto na primeira posição e, a quantidade de chamadas na segunda posição
  public String[] leVetores(Context context,String texto){

    String[] auxiliares = new String[2];
    try {

      File arquivo = new File(context.getFilesDir(), "vetores.txt");
      FileReader fileReader = new FileReader(arquivo);
      BufferedReader reader = new BufferedReader(fileReader);
      String linha;

      while((linha = reader.readLine())!=null){

        String text = linha;
        String substring = text.substring(0,text.indexOf('-'));

        if(substring.equals(texto)) {

          String valor= text.substring(text.indexOf('-') + 1);
          auxiliares[0] = texto;
          auxiliares[1] = valor;
        }
      }

      reader.close();
      fileReader.close();

    }catch(Exception e)
    {
      e.printStackTrace();
    }
    return auxiliares;

  }
  //-------------------------------------------------------------------------------------------------------
  //Método contaLinha
  //Retorna a quantidade de linhas do documento vetores.txt
  public int contaLinha(Context context){

    int num = 0;
    int contagem = 0;

    try{
      File arquivo = new File(context.getFilesDir(), "vetores.txt");
      FileReader fileReader = new FileReader(arquivo);
      BufferedReader reader = new BufferedReader(fileReader);
      String linha;

      while ((linha = reader.readLine()) != null){
        num ++;
      }

      reader.close();
      fileReader.close();
    }
    catch(Exception e){e.printStackTrace();}

    contagem = num + 1;
    return contagem;
  }
  //-------------------------------------------------------------------------------------------------------
  //Método preencheStr
  //Este método retorna um vetor com todo o conteúdo do arquivo vetores.txt, armazenando em cada uma de
  //suas posições o conteúdo de cada linha do documento
  public String[] preencheStr(Context context){

    int numLinha = contaLinha(context);
    int numLinha2 = 0;
    String[] arq = new String[numLinha];
    String linha;

    try {

      File arquivo = new File(context.getFilesDir(), "vetores.txt");
      FileReader fileReader = new FileReader(arquivo);
      BufferedReader reader = new BufferedReader(fileReader);

      while((linha = reader.readLine())!=null){

        String text = linha;
        arq[numLinha2] = text;
        numLinha2++;

      }

      reader.close();
      fileReader.close();

    }catch(Exception e)
    {
      e.printStackTrace();
    }

    return arq;
  }
  //-------------------------------------------------------------------------------------------------------
  //Método addVetor
  //Este método adiciona um texto (representando um objeto de detecção) e a quantidade de chamadas correspondentes
  @RequiresApi(api = Build.VERSION_CODES.O)
  public void addVetor(Context context, String text){

    int num1 = contaLinha(context);
    String t = Integer.toString(num1);
    Log.d("num1",t);
    String[] vetor = new String[num1];
    try {

      vetor = preencheStr(context);
      int leng = vetor.length;
      int num = 0;
      int n = 0;
      String aux[];
      aux = leVetores(context, text);
      String linha;

      //Este trecho funciona
      File arquivo = new File(context.getFilesDir(), "vetores.txt");

      FileOutputStream output = new FileOutputStream(arquivo, true);
      FileOutputStream output2 = new FileOutputStream(arquivo, false);

      OutputStreamWriter outputWriter = new OutputStreamWriter(output);
      OutputStreamWriter outputWriter2 = new OutputStreamWriter(output2);

      FileReader fileReader = new FileReader(arquivo);
      BufferedReader reader = new BufferedReader(fileReader);

      for(int i = 0; i <vetor.length - 1;i++) {
        if (text.equals(aux[0])) {

          Log.d("Ponto4", "foi");
          String v = aux[1];
          int x = Integer.parseInt(v) + 1;
          v = String.valueOf(x);
          String lin;

          if((leng-1) <= 1){
            lin = text + "-" + v;
          }else{

            lin = "\n" + text + "-" + v;}
          String sub = vetor[i];
          String valor= sub.substring(0,sub.indexOf('-'));

          if(valor.equals(text)) {
            vetor[i] = lin;
            n = 1;
          }
        }
      }

      if (n == 0) {

        if((leng-1) == 0){

          vetor[leng-1] = text + "-"+ "1";

        }else{
          vetor[leng-1] = "\n" +  text + "-"+ "1";}

        for(int i=0; i<vetor.length; i++){
          outputWriter.write(vetor[i]);
        }
      }else{

        int lop = 0;
        while(vetor[lop]!=null){
          outputWriter2.write(vetor[lop]);
          lop++;
        }
      }
      outputWriter2.close();
      outputWriter.close();
      reader.close();
      fileReader.close();

    }
    catch(Exception e)
    {
      e.printStackTrace();
    }
  }
  //--------------------------------------------------------------------------------------------------------
  //Método criaTranslate2
  /*Este método recebe um texto e, com base nas referências do arquivo translates.txt, retorna o equivalente a tradução do
  * mesmo para o português. Este método também cria o arquivo translates.txt, se necessário*/
  public String criaTranslate2(String texto, Context cont){

    String traducao= "";

    try{

      AssetManager assetManager = cont.getResources().getAssets();
      InputStream inputStream = assetManager.open("translate.txt");
      BufferedReader reader = new BufferedReader( new InputStreamReader( inputStream ) );
      String linha;
      Log.d("linha 129","foi");
      while((linha = reader.readLine())!=null){

        Log.d("linha 132","foi");
        String text = linha;
        String substring = text.substring(0,text.indexOf('='));

        if(substring.equals(texto)) {

          Log.d("linha 146","foi");
          String auxiliar = linha.substring(linha.indexOf('=') + 1);
          traducao = auxiliar;
          Log.d("traducao", traducao);
        }

      }
      reader.close();

    }catch(Exception e)

    {
      e.printStackTrace();
    }
    return traducao;
  }
  //----------------------------------------------------------------------------------------------------
  //Método processResults
  private void processResults(final List<Recognition> results) {
    final List<Pair<Float, Recognition>> rectsToTrack = new LinkedList<Pair<Float, Recognition>>();

    screenRects.clear();
    final Matrix rgbFrameToScreen = new Matrix(getFrameToCanvasMatrix());

    for (final Recognition result : results) {
      if (result.getLocation() == null) {
        continue;
      }
      final RectF detectionFrameRect = new RectF(result.getLocation());

      final RectF detectionScreenRect = new RectF();
      rgbFrameToScreen.mapRect(detectionScreenRect, detectionFrameRect);

      logger.v(
          "Result! Frame: " + result.getLocation() + " mapped to screen:" + detectionScreenRect);

      screenRects.add(new Pair<Float, RectF>(result.getConfidence(), detectionScreenRect));

      if (detectionFrameRect.width() < MIN_SIZE || detectionFrameRect.height() < MIN_SIZE) {
        logger.w("Degenerate rectangle! " + detectionFrameRect);
        continue;
      }

      rectsToTrack.add(new Pair<Float, Recognition>(result.getConfidence(), result));
    }

    trackedObjects.clear();
    if (rectsToTrack.isEmpty()) {
      logger.v("Nothing to track, aborting.");
      return;
    }

    for (final Pair<Float, Recognition> potential : rectsToTrack) {
      final TrackedRecognition trackedRecognition = new TrackedRecognition();
      trackedRecognition.detectionConfidence = potential.first;
      trackedRecognition.location = new RectF(potential.second.getLocation());
      trackedRecognition.title = potential.second.getTitle();
      trackedRecognition.color = COLORS[trackedObjects.size()];
      trackedObjects.add(trackedRecognition);

      if (trackedObjects.size() >= COLORS.length) {
        break;
      }
    }
  }

  //--------------------------------------------------------------------------------------------------------
  //Método TrackedRecognition
  private static class TrackedRecognition {
    RectF location;
    float detectionConfidence;
    int color;
    String title;
  }

}//Fim da classe
