/*
 * Copyright 2019 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.tensorflow.lite.examples.detection;

//Importações
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Configuration;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ImageReader;
import android.media.ImageReader.OnImageAvailableListener;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.text.TextUtils;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.tensorflow.lite.examples.detection.customview.AutoFitTextureView;
import org.tensorflow.lite.examples.detection.env.Logger;

//--------------------------------------------------------------------------------------------------------
//Classe CarmeraConnectionFragment
@SuppressLint("ValidFragment")
public class CameraConnectionFragment extends Fragment {

  //--------------------------------------------------------------------------------------------------------
  //Atributos
  private static final Logger LOGGER = new Logger();
  /**
   * O tamanho da visualização da câmera será escolhido para ser o menor quadro por tamanho de pixel capaz de
   * contendo um quadrado DESIRED_SIZE x DESIRED_SIZE.
   */
  private static final int MINIMUM_PREVIEW_SIZE = 320;
  /** Conversão da rotação da tela para orientação JPEG. */
  private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
  private static final String FRAGMENT_DIALOG = "dialog";

  static {
    ORIENTATIONS.append(Surface.ROTATION_0, 90);
    ORIENTATIONS.append(Surface.ROTATION_90, 0);
    ORIENTATIONS.append(Surface.ROTATION_180, 270);
    ORIENTATIONS.append(Surface.ROTATION_270, 180);
  }

  /** Um {@link Semaphore} para evitar que o aplicativo saia antes de fechar a câmera. */
  private final Semaphore cameraOpenCloseLock = new Semaphore(1);
  /** Um {@link OnImageAvailableListener} para receber quadros à medida que estiverem disponíveis. */
  private final OnImageAvailableListener imageListener;
  /** O tamanho de entrada em pixels desejado pelo TensorFlow (largura e altura de um bitmap quadrado). */
  private final Size inputSize;
  /** O identificador de layout a ser inflado para este fragmento. */
  private final int layout;
  private final ConnectionCallback cameraConnectionCallback;
  private final CameraCaptureSession.CaptureCallback captureCallback =
      new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureProgressed(
            final CameraCaptureSession session,
            final CaptureRequest request,
            final CaptureResult partialResult) {}

        @Override
        public void onCaptureCompleted(
            final CameraCaptureSession session,
            final CaptureRequest request,
            final TotalCaptureResult result) {}
      };
  /** ID da corrente {@link CameraDevice}. */
  private String cameraId;
  /** Um {@link AutoFitTextureView} para visualização da câmera. */
  private AutoFitTextureView textureView;
  /** Um {@link CameraCaptureSession } para visualização da câmera */
  private CameraCaptureSession captureSession;
  /** Uma referência para abertura {@link CameraDevice}. */
  private CameraDevice cameraDevice;
  /** A rotação em graus do sensor da câmera na tela. */
  private Integer sensorOrientation;
  /** O {@link Size} da visualização da câmera */
  private Size previewSize;
  /** Um thread adicional para executar tarefas que não devem bloquear a IU. */
  private HandlerThread backgroundThread;
  /** Um {@link Handler} para executar tarefas em segundo plano. */
  private Handler backgroundHandler;
  /** Um {@link ImageReader} que controla a captura de quadros de visualização */
  private ImageReader previewReader;
  /** {@link CaptureRequest.Builder} para a visualização da câmera */
  private CaptureRequest.Builder previewRequestBuilder;
  /** {@link CaptureRequest} gerado por {@link #previewRequestBuilder} */
  private CaptureRequest previewRequest;
  /** {@link CameraDevice.StateCallback} é chamado quando {@link CameraDevice} muda de status */
  private final CameraDevice.StateCallback stateCallback =
      new CameraDevice.StateCallback() {
        //Método anOpened
    @Override
        public void onOpened(final CameraDevice cd) {
          // Este método é chamado quando a câmera é aberta. Começamos a visualização da câmera aqui.
          cameraOpenCloseLock.release();
          cameraDevice = cd;
          createCameraPreviewSession();
        }

        //Método onDisconnected
        @Override
        public void onDisconnected(final CameraDevice cd) {
          cameraOpenCloseLock.release();
          cd.close();
          cameraDevice = null;
        }

        //Método onError
        @Override
        public void onError(final CameraDevice cd, final int error) {
          cameraOpenCloseLock.release();
          cd.close();
          cameraDevice = null;
          final Activity activity = getActivity();
          if (null != activity) {
            activity.finish();
          }
        }
      };
  /**
   * {@link TextureView.SurfaceTextureListener} lida com vários eventos de ciclo de vida em um {@link
   * TextureView}.
   */
  private final TextureView.SurfaceTextureListener surfaceTextureListener =
      new TextureView.SurfaceTextureListener() {

    //Método onSurfaceTextureAvaliable
        @Override
        public void onSurfaceTextureAvailable(
            final SurfaceTexture texture, final int width, final int height) {
          openCamera(width, height);
        }

        //Método onSurfaceTextureSizeChanged
        @Override
        public void onSurfaceTextureSizeChanged(
            final SurfaceTexture texture, final int width, final int height) {
          configureTransform(width, height);
        }

        //Método onSurfaceDestroyed
        @Override
        public boolean onSurfaceTextureDestroyed(final SurfaceTexture texture) {
          return true;
        }

        //Método onSurfaceTextureUpdated
        @Override
        public void onSurfaceTextureUpdated(final SurfaceTexture texture) {}
      };

  //--------------------------------------------------------------------------------------------------------
  //Método construtor
  private CameraConnectionFragment(
      final ConnectionCallback connectionCallback,
      final OnImageAvailableListener imageListener,
      final int layout,
      final Size inputSize) {
    this.cameraConnectionCallback = connectionCallback;
    this.imageListener = imageListener;
    this.layout = layout;
    this.inputSize = inputSize;
  }

  //--------------------------------------------------------------------------------------------------------
  /**Método chooseOptimalSize
   * Dá {@code choices} de {@code Size}s apoiado por uma câmera, escolhe o menor cuja
   * largura e altura são pelo menos tão grandes quanto o mínimo de ambas, ou uma correspondência exata, se possível.
   *
   * @param choices A lista de tamanhos que a câmera suporta para a classe de saída pretendida
   * @param width A largura mínima desejada
   * @param height A altura mínima desejada
   * @return O {@code Size} ótimo, ou um arbitrário se nenhum fosse grande o suficiente
   */
  protected static Size chooseOptimalSize(final Size[] choices, final int width, final int height) {
    final int minSize = Math.max(Math.min(width, height), MINIMUM_PREVIEW_SIZE);
    final Size desiredSize = new Size(width, height);

    // Colete as resoluções suportadas que são pelo menos tão grandes quanto a superfície de visualização
    boolean exactSizeFound = false;
    final List<Size> bigEnough = new ArrayList<Size>();
    final List<Size> tooSmall = new ArrayList<Size>();
    for (final Size option : choices) {
      if (option.equals(desiredSize)) {
        // Defina o tamanho, mas não retorne ainda para que os tamanhos restantes ainda sejam registrados.
        exactSizeFound = true;
      }

      if (option.getHeight() >= minSize && option.getWidth() >= minSize) {
        bigEnough.add(option);
      } else {
        tooSmall.add(option);
      }
    }

    LOGGER.i("Desired size: " + desiredSize + ", min size: " + minSize + "x" + minSize);
    LOGGER.i("Valid preview sizes: [" + TextUtils.join(", ", bigEnough) + "]");
    LOGGER.i("Rejected preview sizes: [" + TextUtils.join(", ", tooSmall) + "]");

    if (exactSizeFound) {
      LOGGER.i("Exact size match found.");
      return desiredSize;
    }

    // Escolha o menor deles, assumindo que encontramos algum
    if (bigEnough.size() > 0) {
      final Size chosenSize = Collections.min(bigEnough, new CompareSizesByArea());
      LOGGER.i("Chosen size: " + chosenSize.getWidth() + "x" + chosenSize.getHeight());
      return chosenSize;
    } else {
      LOGGER.e("Couldn't find any suitable preview size");
      return choices[0];
    }
  }

  //--------------------------------------------------------------------------------------------------------
  //Método construtor
  public static CameraConnectionFragment newInstance(
      final ConnectionCallback callback,
      final OnImageAvailableListener imageListener,
      final int layout,
      final Size inputSize) {
    return new CameraConnectionFragment(callback, imageListener, layout, inputSize);
  }

  //--------------------------------------------------------------------------------------------------------
  /**Método showToast
   * Mostra um {@link Toast} na tread UI
   *
   * @param text a mensagem mostrada
   */
  private void showToast(final String text) {
    final Activity activity = getActivity();
    if (activity != null) {
      activity.runOnUiThread(
          new Runnable() {
            @Override
            public void run() {
              Toast.makeText(activity, text, Toast.LENGTH_SHORT).show();
            }
          });
    }
  }

  //--------------------------------------------------------------------------------------------------------
  //Método onCreateView
  @Override
  public View onCreateView(
      final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
    return inflater.inflate(layout, container, false);
  }

  //--------------------------------------------------------------------------------------------------------
  //Método onViewCreated
  @Override
  public void onViewCreated(final View view, final Bundle savedInstanceState) {
    textureView = (AutoFitTextureView) view.findViewById(R.id.texture);
  }

  //--------------------------------------------------------------------------------------------------------
  //Método onActivityCreated
  @Override
  public void onActivityCreated(final Bundle savedInstanceState) {
    super.onActivityCreated(savedInstanceState);
  }

  //--------------------------------------------------------------------------------------------------------
  //Método onResume
  @Override
  public void onResume() {
    super.onResume();
    startBackgroundThread();

    // Quando a tela é desligada e ligada novamente, o SurfaceTexture já está
    // disponível e "onSurfaceTextureAvailable" não será chamado. Nesse caso, podemos abrir
    // uma câmera e iniciar a visualização a partir daqui (caso contrário, esperamos até que a superfície esteja pronta em
    // o SurfaceTextureListener).
    if (textureView.isAvailable()) {
      openCamera(textureView.getWidth(), textureView.getHeight());
    } else {
      textureView.setSurfaceTextureListener(surfaceTextureListener);
    }
  }

  //--------------------------------------------------------------------------------------------------------
  //Método onPause
  @Override
  public void onPause() {
    closeCamera();
    stopBackgroundThread();
    super.onPause();
  }

  //--------------------------------------------------------------------------------------------------------
  //Método setCamera
  public void setCamera(String cameraId) {
    this.cameraId = cameraId;
  }

  //--------------------------------------------------------------------------------------------------------
  //Método setUpCameraOutputs
  /** Configura variáveis de membro relacionadas à câmera. */
  private void setUpCameraOutputs() {
    final Activity activity = getActivity();
    final CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
    try {
      final CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);

      final StreamConfigurationMap map =
          characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

      sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);

      // Perigo, W.R.! A tentativa de usar um tamanho de visualização muito grande pode exceder a câmera
      // limitação de largura de banda do barramento, resultando em belas visualizações, mas o armazenamento de
      // dados de captura de lixo.
      previewSize =
          chooseOptimalSize(
              map.getOutputSizes(SurfaceTexture.class),
              inputSize.getWidth(),
              inputSize.getHeight());

      // Ajustamos a proporção de aspecto de TextureView ao tamanho da visualização que escolhemos.
      final int orientation = getResources().getConfiguration().orientation;
      if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
        textureView.setAspectRatio(previewSize.getWidth(), previewSize.getHeight());
      } else {
        textureView.setAspectRatio(previewSize.getHeight(), previewSize.getWidth());
      }
    } catch (final CameraAccessException e) {
      LOGGER.e(e, "Exception!");
    } catch (final NullPointerException e) {
      // Atualmente, um NPE é lançado quando o Camera2API é usado, mas não é compatível com o
      // dispositivo este código é executado.
      ErrorDialog.newInstance(getString(R.string.tfe_od_camera_error))
          .show(getChildFragmentManager(), FRAGMENT_DIALOG);
      throw new IllegalStateException(getString(R.string.tfe_od_camera_error));
    }

    cameraConnectionCallback.onPreviewSizeChosen(previewSize, sensorOrientation);
  }

  //--------------------------------------------------------------------------------------------------------
  //Método openCamera
  /** Abre a câmera especificada por {@link CameraConnectionFragment#cameraId}. */
  private void openCamera(final int width, final int height) {
    setUpCameraOutputs();
    configureTransform(width, height);
    final Activity activity = getActivity();
    final CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
    try {
      if (!cameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
        throw new RuntimeException("Time out waiting to lock camera opening.");
      }
      manager.openCamera(cameraId, stateCallback, backgroundHandler);
    } catch (final CameraAccessException e) {
      LOGGER.e(e, "Exception!");
    } catch (final InterruptedException e) {
      throw new RuntimeException("Interrupted while trying to lock camera opening.", e);
    }
  }

  //--------------------------------------------------------------------------------------------------------
  //Método closeCamera
  /** Fecha a atual {@link CameraDevice}. */
  private void closeCamera() {
    try {
      cameraOpenCloseLock.acquire();
      if (null != captureSession) {
        captureSession.close();
        captureSession = null;
      }
      if (null != cameraDevice) {
        cameraDevice.close();
        cameraDevice = null;
      }
      if (null != previewReader) {
        previewReader.close();
        previewReader = null;
      }
    } catch (final InterruptedException e) {
      throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
    } finally {
      cameraOpenCloseLock.release();
    }
  }

  //--------------------------------------------------------------------------------------------------------
  //Método startBackgroundThread
  /** Inicia uma tread de fundo e um {@link Handler}. */
  private void startBackgroundThread() {
    backgroundThread = new HandlerThread("ImageListener");
    backgroundThread.start();
    backgroundHandler = new Handler(backgroundThread.getLooper());
  }

  //Método stopBackgroundThread
  /** Para a tread de fundo e o {@link Handler}. */
  private void stopBackgroundThread() {
    backgroundThread.quitSafely();
    try {
      backgroundThread.join();
      backgroundThread = null;
      backgroundHandler = null;
    } catch (final InterruptedException e) {
      LOGGER.e(e, "Exception!");
    }
  }

  //--------------------------------------------------------------------------------------------------------
  //Método createCameraPreviewSession
  /** Cria um novo {@link CameraCaptureSession} para visualização da câmera */
  private void createCameraPreviewSession() {
    try {
      final SurfaceTexture texture = textureView.getSurfaceTexture();
      assert texture != null;

      // Configuramos o tamanho do buffer padrão para ser o tamanho da visualização da câmera que desejamos.
      texture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());

      // Esta é a superfície de saída de que precisamos para iniciar a visualização.
      final Surface surface = new Surface(texture);

      // Configuramos um CaptureRequest.Builder com a superfície de saída.
      previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
      previewRequestBuilder.addTarget(surface);

      LOGGER.i("Opening camera preview: " + previewSize.getWidth() + "x" + previewSize.getHeight());

      // Crie o leitor para os quadros de visualização.
      previewReader =
          ImageReader.newInstance(
              previewSize.getWidth(), previewSize.getHeight(), ImageFormat.YUV_420_888, 2);

      previewReader.setOnImageAvailableListener(imageListener, backgroundHandler);
      previewRequestBuilder.addTarget(previewReader.getSurface());

      // Aqui, criamos uma CameraCaptureSession para visualização da câmera.
      cameraDevice.createCaptureSession(
          Arrays.asList(surface, previewReader.getSurface()),
          new CameraCaptureSession.StateCallback() {

            @Override
            public void onConfigured(final CameraCaptureSession cameraCaptureSession) {
              // A camera ja esta fechada
              if (null == cameraDevice) {
                return;
              }

              // Quando a sessão estiver pronta, começamos a exibir a visualização.
              captureSession = cameraCaptureSession;
              try {
                // O foco automático deve ser contínuo para a visualização da câmera.
                previewRequestBuilder.set(
                    CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                // O Flash é ativado automaticamente quando necessário.
                previewRequestBuilder.set(
                    CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);

                // Finalmente, começamos a exibir a visualização da câmera.
                previewRequest = previewRequestBuilder.build();
                captureSession.setRepeatingRequest(
                    previewRequest, captureCallback, backgroundHandler);
              } catch (final CameraAccessException e) {
                LOGGER.e(e, "Exception!");
              }
            }

            @Override
            public void onConfigureFailed(final CameraCaptureSession cameraCaptureSession) {
              showToast("Failed");
            }
          },
          null);
    } catch (final CameraAccessException e) {
      LOGGER.e(e, "Exception!");
    }
  }

  //--------------------------------------------------------------------------------------------------------
  /**Método configureTransform
   * Configura a transformação {@link Matrix} necessária para `mTextureView`. Este método deve ser
   *chamado depois que o tamanho da visualização da câmera é determinado em setUpCameraOutputs e também o tamanho de
   *`mTextureView` foi corrigido.
   *
   * @param viewWidth A largura de `mTextureView`
   * @param viewHeight A altura de `mTextureView`
   */
  private void configureTransform(final int viewWidth, final int viewHeight) {
    final Activity activity = getActivity();
    if (null == textureView || null == previewSize || null == activity) {
      return;
    }
    final int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
    final Matrix matrix = new Matrix();
    final RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
    final RectF bufferRect = new RectF(0, 0, previewSize.getHeight(), previewSize.getWidth());
    final float centerX = viewRect.centerX();
    final float centerY = viewRect.centerY();
    if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
      bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
      matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
      final float scale =
          Math.max(
              (float) viewHeight / previewSize.getHeight(),
              (float) viewWidth / previewSize.getWidth());
      matrix.postScale(scale, scale, centerX, centerY);
      matrix.postRotate(90 * (rotation - 2), centerX, centerY);
    } else if (Surface.ROTATION_180 == rotation) {
      matrix.postRotate(180, centerX, centerY);
    }
    textureView.setTransform(matrix);
  }

  //--------------------------------------------------------------------------------------------------------
  /**Método ConnectionCallback
   * Retorno de chamada para atividades a serem usadas para inicializar seus dados assim que o tamanho de visualização selecionado for
   *conhecido.
   */
  public interface ConnectionCallback {
    void onPreviewSizeChosen(Size size, int cameraRotation);
  }

  //--------------------------------------------------------------------------------------------------------
  //Método CompareSizesByArea
  /** Compara dois {@code Size}s baseado em suas áreas */
  static class CompareSizesByArea implements Comparator<Size> {
    @Override
    public int compare(final Size lhs, final Size rhs) {
      // Nós lançamos aqui para garantir que as multiplicações não estourem
      return Long.signum(
          (long) lhs.getWidth() * lhs.getHeight() - (long) rhs.getWidth() * rhs.getHeight());
    }
  }

  //--------------------------------------------------------------------------------------------------------
  //Método ErrorDialog
  /** Mostra uma caixa de diálogo de mensagem de erro. */
  public static class ErrorDialog extends DialogFragment {
    private static final String ARG_MESSAGE = "message";

    public static ErrorDialog newInstance(final String message) {
      final ErrorDialog dialog = new ErrorDialog();
      final Bundle args = new Bundle();
      args.putString(ARG_MESSAGE, message);
      dialog.setArguments(args);
      return dialog;
    }

    @Override
    public Dialog onCreateDialog(final Bundle savedInstanceState) {
      final Activity activity = getActivity();
      return new AlertDialog.Builder(activity)
          .setMessage(getArguments().getString(ARG_MESSAGE))
          .setPositiveButton(
              android.R.string.ok,
              new DialogInterface.OnClickListener() {
                @Override
                public void onClick(final DialogInterface dialogInterface, final int i) {
                  activity.finish();
                }
              })
          .create();
    }
  }

}//Fim da classe
