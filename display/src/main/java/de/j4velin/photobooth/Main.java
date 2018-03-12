package de.j4velin.photobooth;

import android.app.Application;
import android.content.Intent;
import android.graphics.Bitmap;
import android.support.constraint.BuildConfig;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public class Main extends Application implements ITrigger.TriggerCallback, ICamera.CameraCallback {
    public final static String TAG = "photobooth";

    private final List<ICamera> cameras = new ArrayList<>(2);
    private final List<IDisplay> displays = new ArrayList<>(1);
    private final List<ITrigger> triggers = new ArrayList<>(1);

    private final Comparator<ICamera> cameraComparator = new ICamera.CameraComparator();

    private final static AtomicBoolean STARTED = new AtomicBoolean(false);

    public void start() {
        synchronized (STARTED) {
            if (!STARTED.getAndSet(true)) {
                if (BuildConfig.DEBUG) Log.i(Main.TAG, "starting");
                if (BuildConfig.FLAVOR.equalsIgnoreCase("gopro")) {
                    addCamera(new GoProCamera(getApplicationContext()));
                } else {
                    startService(new Intent(getApplicationContext(), SocketCameraService.class));
                }
                startService(new Intent(getApplicationContext(), SocketTriggerService.class));
            }
        }
    }

    public void stop() {
        synchronized (STARTED) {
            if (STARTED.getAndSet(false)) {
                if (BuildConfig.DEBUG) Log.i(Main.TAG, "stopping");
                for (ITrigger trigger : triggers) {
                    trigger.disableTrigger();
                }
                for (ICamera camera : cameras) {
                    camera.shutdownCamera(this);
                }
                triggers.clear();
                cameras.clear();
                displays.clear();
            }
        }
    }

    public void addCamera(final ICamera camera) {
        cameras.add(Objects.requireNonNull(camera, "camera must not be null"));
        Collections.sort(cameras, cameraComparator);
        camera.addPhotoTakenCallback(this);
    }

    public void removeCamera(final ICamera camera) {
        cameras.remove(Objects.requireNonNull(camera, "camera must not be null"));
    }

    public void addDisplay(final IDisplay display) {
        displays.add(Objects.requireNonNull(display, "display must not be null"));
    }

    public void removeDisplay(final IDisplay display) {
        displays.remove(Objects.requireNonNull(display, "display must not be null"));
    }

    public void addTrigger(final ITrigger trigger) {
        triggers.add(Objects.requireNonNull(trigger, "trigger must not be null"));
        trigger.enableTrigger(this);
    }

    public void removeTrigger(final ITrigger trigger) {
        triggers.remove(Objects.requireNonNull(trigger, "trigger must not be null"));
        trigger.disableTrigger();
    }

    @Override
    public void takePhoto() {
        if (BuildConfig.DEBUG) Log.d(Main.TAG, "Taking photo...");
        for (IDisplay display : displays) {
            display.showWait();
        }
        boolean mainCameraReady = false;
        for (ICamera camera : cameras) {
            if (mainCameraReady && camera.getCameraType() == ICamera.Type.Backup) {
                break;
            }
            if (camera.cameraIsReady()) {
                camera.takePhoto();
                if (camera.getCameraType() == ICamera.Type.Main) {
                    mainCameraReady = true;
                }
            }
        }
    }

    @Override
    public void imageReady(final Bitmap image) {
        if (BuildConfig.DEBUG) Log.d(Main.TAG, "Image ready");
        for (IDisplay display : displays) {
            display.displayImage(image);
        }
    }

    @Override
    public void error() {
        if (BuildConfig.DEBUG) Log.w(Main.TAG, "error while taking photo");
        for (IDisplay display : displays) {
            display.abortShowWait();
        }
    }
}