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
import android.util.Log;
import java.util.HashSet;
import java.util.Set;

//---------------------------------------------------------------------------------------------------
//Classe Logger
/** Wrapper para a função de log da plataforma, permite prefixar mensagens convenientes e desativar o log. */
public final class Logger {

  //---------------------------------------------------------------------------------------------------
  //Atributos
  private static final String DEFAULT_TAG = "tensorflow";
  private static final int DEFAULT_MIN_LOG_LEVEL = Log.DEBUG;
  // Classes a serem ignoradas ao examinar o rastreamento de pilha
  private static final Set<String> IGNORED_CLASS_NAMES;

  static {
    IGNORED_CLASS_NAMES = new HashSet<String>(3);
    IGNORED_CLASS_NAMES.add("dalvik.system.VMStack");
    IGNORED_CLASS_NAMES.add("java.lang.Thread");
    IGNORED_CLASS_NAMES.add(Logger.class.getCanonicalName());
  }

  private final String tag;
  private final String messagePrefix;
  private int minLogLevel = DEFAULT_MIN_LOG_LEVEL;

  //---------------------------------------------------------------------------------------------------
  /**Métodos Construtores
   * Cria um Logger usando o nome da classe como prefixo da mensagem.
   *
   * @param clazz o nome simples desta classe é usado como o prefixo da mensagem.
   */
  public Logger(final Class<?> clazz) {
    this(clazz.getSimpleName());
  }

  /**
   * Cria um Logger usando o prefixo de mensagem especificado.
   *
   * @param messagePrefix é anexado ao texto de cada mensagem.
   */
  public Logger(final String messagePrefix) {
    this(DEFAULT_TAG, messagePrefix);
  }

  /**
   * Cria um Logger com uma tag personalizada e um prefixo de mensagem personalizado. Se o prefixo da mensagem for definido como
   *
   * <pre>null</pre>
   *
   * , o nome da classe do chamador é usado como prefixo.
   *
   * @param tag identifica a origem de uma mensagem de log.
   * @param messagePrefix prefixado a cada mensagem se não for nulo. Se nulo, o nome do chamador está
   *  sendo usado
   */
  public Logger(final String tag, final String messagePrefix) {
    this.tag = tag;
    final String prefix = messagePrefix == null ? getCallerSimpleName() : messagePrefix;
    this.messagePrefix = (prefix.length() > 0) ? prefix + ": " : prefix;
  }

  /** Cria um Logger usando o nome da classe do chamador como o prefixo da mensagem. */
  public Logger() {
    this(DEFAULT_TAG, null);
  }

  /** Cria um Logger usando o nome da classe do chamador como o prefixo da mensagem. */
  public Logger(final int minLogLevel) {
    this(DEFAULT_TAG, null);
    this.minLogLevel = minLogLevel;
  }

  //---------------------------------------------------------------------------------------------------
  /**Método getCallerSimpleName
   * Retorne o nome simples do chamador.
   *
   * <p>Android getStackTrace() retorna um array parecido com este: stackTrace[0]:
   * dalvik.system.VMStack stackTrace[1]: java.lang.Thread stackTrace[2]:
   * com.google.android.apps.unveil.env.UnveilLogger stackTrace[3]:
   * com.google.android.apps.unveil.BaseApplication
   *
   * <p>Esta função retorna a versão simples do primeiro nome não filtrado.
   *
   * @return nome simples do chamador
   */
  private static String getCallerSimpleName() {
    // Obtenha a pilha de chamadas atual para que possamos retirar a classe do chamador dela.
    final StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();

    for (final StackTraceElement elem : stackTrace) {
      final String className = elem.getClassName();
      if (!IGNORED_CLASS_NAMES.contains(className)) {
        // Estamos interessados apenas no nome simples da classe, não no pacote completo.
        final String[] classParts = className.split("\\.");
        return classParts[classParts.length - 1];
      }
    }

    return Logger.class.getSimpleName();
  }

  //---------------------------------------------------------------------------------------------------
  //Métodos para manipulação dos diferentes Logs
  public void setMinLogLevel(final int minLogLevel) {
    this.minLogLevel = minLogLevel;
  }

  public boolean isLoggable(final int logLevel) {
    return logLevel >= minLogLevel || Log.isLoggable(tag, logLevel);
  }

  private String toMessage(final String format, final Object... args) {
    return messagePrefix + (args.length > 0 ? String.format(format, args) : format);
  }

  public void v(final String format, final Object... args) {
    if (isLoggable(Log.VERBOSE)) {
      Log.v(tag, toMessage(format, args));
    }
  }

  public void v(final Throwable t, final String format, final Object... args) {
    if (isLoggable(Log.VERBOSE)) {
      Log.v(tag, toMessage(format, args), t);
    }
  }

  public void d(final String format, final Object... args) {
    if (isLoggable(Log.DEBUG)) {
      Log.d(tag, toMessage(format, args));
    }
  }

  public void d(final Throwable t, final String format, final Object... args) {
    if (isLoggable(Log.DEBUG)) {
      Log.d(tag, toMessage(format, args), t);
    }
  }

  public void i(final String format, final Object... args) {
    if (isLoggable(Log.INFO)) {
      Log.i(tag, toMessage(format, args));
    }
  }

  public void i(final Throwable t, final String format, final Object... args) {
    if (isLoggable(Log.INFO)) {
      Log.i(tag, toMessage(format, args), t);
    }
  }

  public void w(final String format, final Object... args) {
    if (isLoggable(Log.WARN)) {
      Log.w(tag, toMessage(format, args));
    }
  }

  public void w(final Throwable t, final String format, final Object... args) {
    if (isLoggable(Log.WARN)) {
      Log.w(tag, toMessage(format, args), t);
    }
  }

  public void e(final String format, final Object... args) {
    if (isLoggable(Log.ERROR)) {
      Log.e(tag, toMessage(format, args));
    }
  }

  public void e(final Throwable t, final String format, final Object... args) {
    if (isLoggable(Log.ERROR)) {
      Log.e(tag, toMessage(format, args), t);
    }
  }

}//Fim da classe
