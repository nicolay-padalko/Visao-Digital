package org.tensorflow.lite.examples.detection;

//Importações
import android.content.Context;
import android.graphics.RectF;
import android.speech.tts.TextToSpeech;
import androidx.appcompat.app.AppCompatActivity;

//--------------------------------------------------------------------------------------------------------------------------------
//Classe Utilities
//Esta classe contém os métodos para chamada e manipulação da emissão da mensagem auditiva, através da
//api texttospeech
public class Utilities extends AppCompatActivity{

    //Atributos
    private String textoFalar;
    private String teste;
    private RectF recog;
    private Context context;
    private TextToSpeech tts;
    private String txt;
    private String TAG = Utilities.class.getSimpleName();

//----------------------------------------------------------------------------------------------------------
    //Métodos Getters e Setters
    public void setTeste(String teste){ this.teste = teste;}
    public String getTeste(){return this.teste;}

    public void setRecog(RectF recog){ this.recog = recog;}
    public RectF getRecog(){return this.recog;}

    public void setTextoFalar(String textoFalar){ this.textoFalar = textoFalar;}
    public String getTextoFalar(){return this.textoFalar;}

//----------------------------------------------------------------------------------------------------
    //Métodos Construtores
    public Utilities(Context context, String txt) {
        this.context = context;
        this.txt = txt;
        handleSpeech();
    }

    public Utilities(){}

    private void handleSpeech() {

        tts = new TextToSpeech(context, new TextToSpeech.OnInitListener() {

            @Override
            public void onInit(int status) {

                if (status == TextToSpeech.SUCCESS) {
                    saySomeThing();
                }
            }
        });
    }

//---------------------------------------------------------------------------------------------------
    //Método saySomeThing
    //Método que executa a fala
    private void saySomeThing() {

        if ((txt != null) && (txt.length() > 0)) {
            tts.speak(txt, TextToSpeech.QUEUE_FLUSH, null);
        } else {
            tts.shutdown();
        }
    }

}//Fim da classe
