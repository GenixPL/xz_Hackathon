package pw.xz.xdd.xz;


import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.CardView;
import android.util.Pair;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.indoorway.android.common.sdk.IndoorwaySdk;
import com.indoorway.android.common.sdk.listeners.generic.Action1;
import com.indoorway.android.common.sdk.model.Coordinates;
import com.indoorway.android.common.sdk.model.IndoorwayMap;
import com.indoorway.android.common.sdk.model.IndoorwayObjectParameters;
import com.indoorway.android.common.sdk.model.IndoorwayPosition;
import com.indoorway.android.fragments.sdk.map.IndoorwayMapFragment;
import com.indoorway.android.fragments.sdk.map.MapFragment;
import com.indoorway.android.location.sdk.IndoorwayLocationSdk;
import com.indoorway.android.location.sdk.model.IndoorwayLocationSdkError;
import com.indoorway.android.location.sdk.model.IndoorwayLocationSdkState;
import com.indoorway.android.map.sdk.view.IndoorwayMapView;
import com.indoorway.android.map.sdk.view.drawable.layers.MarkersLayer;

import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Text;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.Time;
import java.time.LocalDateTime;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {
    public class TopExceptionHandler implements Thread.UncaughtExceptionHandler {

        private Thread.UncaughtExceptionHandler defaultUEH;

        private Activity app = null;

        public TopExceptionHandler(Activity app) {
            this.defaultUEH = Thread.getDefaultUncaughtExceptionHandler();
            this.app = app;
        }

        public void uncaughtException(Thread t, Throwable e) {
            StackTraceElement[] arr = e.getStackTrace();
            String report = e.toString() + "\n\n";
            report += "--------- Stack trace ---------\n\n";
            for (int i = 0; i < arr.length; i++) {
                report += "    " + arr[i].toString() + "\n";
            }
            report += "-------------------------------\n\n";

// If the exception was thrown in a background thread inside
// AsyncTask, then the actual exception can be found with getCause
            report += "--------- Cause ---------\n\n";
            Throwable cause = e.getCause();
            if (cause != null) {
                report += cause.toString() + "\n\n";
                arr = cause.getStackTrace();
                for (int i = 0; i < arr.length; i++) {
                    report += "    " + arr[i].toString() + "\n";
                }
            }
            report += "-------------------------------\n\n";

            try {
                FileOutputStream trace = app.openFileOutput(
                        "stack.trace", Context.MODE_PRIVATE);
                trace.write(report.getBytes());
                trace.close();
            } catch (IOException ioe) {
// ...
            }

            defaultUEH.uncaughtException(t, e);
        }
    }

    public IndoorwayMapView indoorwayMapView;
    public IndoorwayMap currentMap;
    public SQLiteDbHelper database;
    public MarkersLayer myLayer;
    private RoomProximityDetector detector;
    private IndoorwayPosition currentPosition;

    TextView tx;
    CardView cardView;
    String tex;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Thread.setDefaultUncaughtExceptionHandler(new TopExceptionHandler(this));
        String line;
        String trace = "";
        try {

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(this
                            .openFileInput("stack.trace")));
            while ((line = reader.readLine()) != null) {
                trace += line + "\n";
            }
        } catch (FileNotFoundException fnfe) {
// ...
        } catch (IOException ioe) {
// ...
        }

        Intent sendIntent = new Intent(Intent.ACTION_SEND);
        String subject = "Error report";
        String body =
                "Mail this to appdeveloper@gmail.com: " +
                        "\n" +
                        trace +
                        "\n";

        sendIntent.putExtra(Intent.EXTRA_EMAIL,
                new String[]{"emilm7843@gmail.com"});
        sendIntent.putExtra(Intent.EXTRA_TEXT, body);
        sendIntent.putExtra(Intent.EXTRA_SUBJECT, subject);
        sendIntent.setType("message/rfc822");

        this.startActivity(
                Intent.createChooser(sendIntent, "Title:"));

        this.deleteFile("stack.trace");

        setContentView(R.layout.activity_main);
        Thread.setDefaultUncaughtExceptionHandler(new TopExceptionHandler(this));
        SQLiteDb sql = new SQLiteDb(getApplicationContext());
        sql.sqliteDbUpdateOnce(getApplicationContext());
        database = new SQLiteDbHelper(getApplicationContext());
        indoorwayMapView = findViewById(R.id.mapView);

        initializeMap();
        displayUser();
        initStatusListeners();

    }

    private void initStatusListeners() {
        Action1<IndoorwayLocationSdkError> sdkErrListener = new Action1<IndoorwayLocationSdkError>() {
            @Override
            public void onAction(IndoorwayLocationSdkError error) {
                if (error instanceof IndoorwayLocationSdkError.BleNotSupported) {
                    // Bluetooth Low Energy is not supported, positioning service will be stopped, it can't work
                    toastMessage("Low energy error");
                } else if (error instanceof IndoorwayLocationSdkError.MissingPermission) {
                    // Some permissions are missing, ask for it.permission
                    toastMessage("Permissions error");
                } else if (error instanceof IndoorwayLocationSdkError.BluetoothDisabled) {
                    // Bluetooth is disabled, user have to turn it on
                    toastMessage("Disabled bluetooth error");
                } else if (error instanceof IndoorwayLocationSdkError.LocationDisabled) {
                    // Location is disabled, user have to turn it on
                    toastMessage("Disabled location error");
                } else if (error instanceof IndoorwayLocationSdkError.UnableToFetchData) {
                    // Network-related error, service will be restarted on network connection established
                    toastMessage("Network-related error");
                } else if (error instanceof IndoorwayLocationSdkError.NoRadioMaps) {
                    // Measurements have to be taken in order to use location
                    toastMessage("Measurements error");
                }
            }
        };

        IndoorwayLocationSdk.instance()
                .state()
                .onError()
                .register(sdkErrListener);

        Action1<IndoorwayLocationSdkState> sdkStateListener = new Action1<IndoorwayLocationSdkState>() {
            @Override
            public void onAction(IndoorwayLocationSdkState indoorwayLocationSdkState) {
                // handle state changes
                toastMessage(indoorwayLocationSdkState.toString());
            }
        };

        IndoorwayLocationSdk.instance()
                .state()
                .onChange()
                .register(sdkStateListener);
    }

    private void displayUser() {
        Action1<IndoorwayPosition> positionListener = new Action1<IndoorwayPosition>() {
            @Override

            public void onAction(IndoorwayPosition position) {
                // store last position as a field
                currentPosition = position;

                // react for position changes...

                // If you are using map view, you can pass position.
                // Second argument indicates if you want to auto reload map on position change
                // for eg. after going to different building level.
                kotlin.Pair<Room, Double> roomData = detector.getNearestRoom(position.getCoordinates());
                Room room = roomData.component1();

                Calendar rightNow = Calendar.getInstance();
                int currentHour = rightNow.get(Calendar.HOUR_OF_DAY);
                int currentMinutes = rightNow.get(Calendar.MINUTE);
                int currentDay = rightNow.get(Calendar.DAY_OF_WEEK);
                String day = JavaDisabilitiesFixerKt.getDays().get(currentDay);


                String roomId = room.getId();

                //database.getByRoomAndTime(room.getId(),currentHour,currentMinutes, day);
                indoorwayMapView.getSelection().selectObject(roomData.component1().getId());
                indoorwayMapView.getPosition().setPosition(currentPosition, true);

                String lastRoomId = room.getId();

                tx = findViewById(R.id.tx);
                tx.setText(room.getId() + "\n" + currentHour + ":" + currentMinutes + ", " + day);
                cardView = findViewById(R.id.card_view);
                tx.setGravity(Gravity.CENTER);

                tx.setVisibility(View.VISIBLE);
                cardView.setVisibility(View.VISIBLE);


                Handler handler = new Handler();
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        tx.setVisibility(View.INVISIBLE);
                        cardView.setVisibility(View.INVISIBLE);
                    }
                }, 3000);


            }
        };

        IndoorwayLocationSdk.instance()
                .position()
                .onChange()
                .register(positionListener);
    }

    private void initializeMap() {
        indoorwayMapView.load("CScrSxCVhQg", "3-_M01M3r5w");

        indoorwayMapView.getTouch().setOnClickListener(new Action1<Coordinates>() {
            @Override
            public void onAction(Coordinates coordinates) {
                //toastMessage(coordinates.toString());
                //indoorwayMapView.getSelection().selectObject();
                try {
                    List<IndoorwayObjectParameters> result = currentMap.objectsContainingCoordinates(coordinates);

                    //tx.setText(result.get(0).getName() + "");
                    tex = result.get(0).getId() + "";
                    toastMessage(tex);
                } catch (Exception e) {
                    toastMessage("error byl");
                }
            }
        });

        indoorwayMapView.setOnMapLoadCompletedListener(new Action1<IndoorwayMap>() {
            @Override
            public void onAction(IndoorwayMap indoorwayMap) {
                currentMap = indoorwayMap;

                detector = new RoomProximityDetector(indoorwayMap, indoorwayMapView);
                detector.getAllRooms();

                myLayer = indoorwayMapView.getMarker().addLayer(0);
            }
        });

    }

    public void toastMessage(String message) {
        //Snackbar snackbar = Snackbar.make(findViewById(R.id.cor), message, 7000);
        //snackbar.show();

        Toast toast = Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG);
        toast.show();

    }

}
