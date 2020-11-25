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

package org.tensorflow.lite.examples.detection.customview;

//Importações
import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.View;
import java.util.LinkedList;
import java.util.List;

//---------------------------------------------------------------------------------------------------
//Classe OverLayView
/* Uma visualização simples que fornece um retorno de chamada de renderização para outras classes. */
public class OverlayView extends View {

//---------------------------------------------------------------------------------------------------
//Atributos
  private final List<DrawCallback> callbacks = new LinkedList<DrawCallback>();

//---------------------------------------------------------------------------------------------------
  //Método construtor
  public OverlayView(final Context context, final AttributeSet attrs) {
    super(context, attrs);
  }

//---------------------------------------------------------------------------------------------------
  //Método addCallBack
  public void addCallback(final DrawCallback callback) {
    callbacks.add(callback);
  }

//---------------------------------------------------------------------------------------------------
  //Método draw
  @Override
  public synchronized void draw(final Canvas canvas) {
    for (final DrawCallback callback : callbacks) {
      callback.drawCallback(canvas);
    }
  }

//---------------------------------------------------------------------------------------------------
  /*Método DrawCallBack
  * Interface que define o retorno de chamada para classes de cliente. */
  public interface DrawCallback {
    public void drawCallback(final Canvas canvas);
  }

}//Fim da Classe
