package org.tensorflow.lite.examples.detection;

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

//Importações
import android.app.Fragment;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.os.Bundle;
import android.os.HandlerThread;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import java.io.IOException;
import java.util.List;
import org.tensorflow.lite.examples.detection.customview.AutoFitTextureView;
import org.tensorflow.lite.examples.detection.env.ImageUtils;
import org.tensorflow.lite.examples.detection.env.Logger;

//--------------------------------------------------------------------------------------------------------
//Classe LegacyCameraConnectionFragment
public class LegacyCameraConnectionFragment extends Fragment {

  //--------------------------------------------------------------------------------------------------------
  //Atributos
  private static final Logger LOGGER = new Logger();
  /** Conversão da rotação da tela para orientação JPEG. */
  private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

  static {
    ORIENTATIONS.append(Surface.ROTATION_0, 90);
    ORIENTATIONS.append(Surface.ROTATION_90, 0);
    ORIENTATIONS.append(Surface.ROTATION_180, 270);
    ORIENTATIONS.append(Surface.ROTATION_270, 180);
  }

  private Camera camera;
  private Camera.PreviewCallback imageListener;
  private Size desiredSize;
  /** O identificador de layout a ser inflado para este fragmento. */
  private int layout;
  /** Um {@link AutoFitTextureView} para visualização da câmera. */
  private AutoFitTextureView textureView;
  private SurfaceTexture availableSurfaceTexture = null;

  /**
   * {@link TextureView.SurfaceTextureListener} lida com vários eventos de ciclo de vida em um {@link
   * TextureView}.
   */
  private final TextureView.SurfaceTextureListener surfaceTextureListener =
      new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(
            final SurfaceTexture texture, final int width, final int height) {
          availableSurfaceTexture = texture;
          startCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(
            final SurfaceTexture texture, final int width, final int height) {}

        @Override
        public boolean onSurfaceTextureDestroyed(final SurfaceTexture texture) {
          return true;
        }

        @Override
        public void onSurfaceTextureUpdated(final SurfaceTexture texture) {}
      };
  /** An additional thread for running tasks that shouldn't block the UI. */
  private HandlerThread backgroundThread;

  //--------------------------------------------------------------------------------------------------------
  //Método construtor
  public LegacyCameraConnectionFragment(
      final Camera.PreviewCallback imageListener, final int layout, final Size desiredSize) {
    this.imageListener = imageListener;
    this.layout = layout;
    this.desiredSize = desiredSize;
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
      startCamera();
    } else {
      textureView.setSurfaceTextureListener(surfaceTextureListener);
    }
  }

  //--------------------------------------------------------------------------------------------------------
  //Método onPause
  @Override
  public void onPause() {
    stopCamera();
    stopBackgroundThread();
    super.onPause();
  }

  //--------------------------------------------------------------------------------------------------------
  /*Método startBackgroudThread
  * Inicia um thread em segundo plano e seu {@link Handler}. */
  private void startBackgroundThread() {
    backgroundThread = new HandlerThread("CameraBackground");
    backgroundThread.start();
  }

  //--------------------------------------------------------------------------------------------------------
  /*Método stopBackgroundThread
  * Interrompe a execução em segundo plano e seu {@link Handler}. */
  private void stopBackgroundThread() {
    backgroundThread.quitSafely();
    try {
      backgroundThread.join();
      backgroundThread = null;
    } catch (final InterruptedException e) {
      LOGGER.e(e, "Exception!");
    }
  }

  //--------------------------------------------------------------------------------------------------------
  //Método startCamera
  private void startCamera() {
    int index = getCameraId();
    camera = Camera.open(index);

    try {
      Camera.Parameters parameters = camera.getParameters();
      List<String> focusModes = parameters.getSupportedFocusModes();
      if (focusModes != null
              && focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
        parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
      }
      List<Camera.Size> cameraSizes = parameters.getSupportedPreviewSizes();
      Size[] sizes = new Size[cameraSizes.size()];
      int i = 0;
      for (Camera.Size size : cameraSizes) {
        sizes[i++] = new Size(size.width, size.height);
      }
      Size previewSize =
              CameraConnectionFragment.chooseOptimalSize(
                      sizes, desiredSize.getWidth(), desiredSize.getHeight());
      parameters.setPreviewSize(previewSize.getWidth(), previewSize.getHeight());
      camera.setDisplayOrientation(90);
      camera.setParameters(parameters);
      camera.setPreviewTexture(availableSurfaceTexture);
    } catch (IOException exception) {
      camera.release();
    }

    camera.setPreviewCallbackWithBuffer(imageListener);
    Camera.Size s = camera.getParameters().getPreviewSize();
    camera.addCallbackBuffer(new byte[ImageUtils.getYUVByteSize(s.height, s.width)]);

    textureView.setAspectRatio(s.height, s.width);

    camera.startPreview();
  }

  //--------------------------------------------------------------------------------------------------------
  //Método stopCamera
  protected void stopCamera() {
    if (camera != null) {
      camera.stopPreview();
      camera.setPreviewCallback(null);
      camera.release();
      camera = null;
    }
  }

  //--------------------------------------------------------------------------------------------------------
  //Método getCameraId
  private int getCameraId() {
    CameraInfo ci = new CameraInfo();
    for (int i = 0; i < Camera.getNumberOfCameras(); i++) {
      Camera.getCameraInfo(i, ci);
      if (ci.facing == CameraInfo.CAMERA_FACING_BACK) return i;
    }
    return -1; // No camera found
  }

}//Fim da classe
