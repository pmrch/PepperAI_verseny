package com.szkkr.pepperai;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.util.Log;
import android.widget.Button;

import androidx.activity.EdgeToEdge;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.aldebaran.qi.Consumer;
import com.aldebaran.qi.Future;
import com.aldebaran.qi.sdk.Qi;
import com.aldebaran.qi.sdk.QiSDK;
import com.szkkr.pepperai.backend.ExecuteEndedListener;
import com.szkkr.pepperai.backend.RobotController;
import com.szkkr.pepperai.backend.depricated.ChatMemory;
import com.szkkr.pepperai.backend.depricated.ChatRequest;
import com.szkkr.pepperai.backend.depricated.ChatResponse;
import com.szkkr.pepperai.backend.depricated.GroqApiService;
import com.szkkr.pepperai.backend.depricated.GroqModels;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends RobotController implements ExecuteEndedListener
{
    private static final String TAG = "MainActivity";
    private Button gomb;

    private final String apiKey = "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"; // Not secure!!!
    private final String systemMessage = "Te a Balassagyarmati Balassi Bálint Gimnázium mesterséges intelligencia alapú robotja vagy. \n" +
            "A te neved Pepi.\n" +
            "\n" +
            "Ha nem kérdezik, hogy hívnak, vagy nem kérdezik mi a neved, akkor csak a kérdésre válaszolj!" +
            "Ha be kell mutatkoznod, akkor azt röviden tedd meg!\n" +
            "Fontos, hogy röviden és érthetően válaszolj a kérdésekre!\n" +
            "Ha trágár kifejezésekkel kérdeznek akkor figyelmeztetsd őt, hogy illedelmesen beszéljen\n" +
            "(Mogyorósi Attlila: Az iskola igazgatója. Ő biológiát tanít.)" +
            "\n";
    //A feladatod, hogy segíts a diákoknak a tanulásban és egyéb iskolához kötődő dolgokban.
    private final String SZOVEG2 = "Sziasztok öcskösök";

    private final String SZOVEG = "Kedves Versenyzők!\n" +
            "Nagyszerű nap végén vagyunk túl, remélem ti is jól éreztétek magatokat. A feladatok megálmodói, a verseny szervezői nevében mondok köszönetet azért, hogy mindent beleadtatok a mai vetélkedőbe, és hogy mindenki sportszerű volt. Nagyon remélem, hogy jó élményekkel tértek haza és erre a napra, az első Tanker Kupára, szívesen fogtok emlékezni. Gratulálunk mindenkinek, aki a mai napon részt vett a játékokban. Fogadjátok szeretettel a mai nap emlékére készült emléklapot.\n" +
            "\n" +
            "Most pedig kihirdetjük a verseny eredményét. A verseny főszervezője, a Balassagyarmati Tankerületi Központ igazgatója, Nagyné Barna Orsolya fogja szólítani azokat a csapatokat, akik a legtöbb pontot szerezték a mai napon.";
    private final GroqApiService apiService = new GroqApiService(apiKey);
    private SpeechManager speechManager = new SpeechManager();
    private volatile ChatMemory memory = new ChatMemory();


    private volatile boolean isHolding = false;
    
    // Direct keyword-response map for simplicity
    private Map<String, String> keywordResponses = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) ->
        {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        //initBtns____________
        gomb = findViewById(R.id.gomb);

        initListeners();

        QiSDK.register(this, this);
    }

    /**
     * Initialize the keyword-response pairs.
     */
    
    /**
     * Check if the input contains any of the defined keywords.
     * 
     * @return the corresponding response if a keyword is found, null otherwise
     */

    public void initListeners()
    {
        gomb.setOnClickListener(v ->
        {
            speechManager.getSpeechInput();
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 100 && resultCode == RESULT_OK && data != null)
        {
            ArrayList<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            String result = results.get(0);

            // Check if the input contains any keywords - simple direct approach
            processWithLLM(result);
        }
    }


    /**
     * Process the user input with the LLM
     */
    private void processWithLLM(String userInput) {
        memory.addSystemMessage(systemMessage);
        memory.addUserMessage(userInput);

        // Execute the API call in a background thread to avoid NetworkOnMainThreadException
        new Thread(() ->
        {
            try {
                ChatRequest request = new ChatRequest();
                request.setModel(GroqModels.LLAMA3_3_70B_VERSATILE.toString());
                request.setMemory(memory);
                
                // API call - This happens in the background thread
                final ChatResponse response = apiService.sendMessage(request);
                final String valasz = response.getContent();

                // Execute the speech on the UI thread
                runOnUiThread(() ->
                {

                     if (!isHolding && userInput.contains("zárd le a versenyt"))
                    {
                        execSay(SZOVEG , () -> animateRobot(ROBOT_HAND_HOLD, this::holdBodyPose));
                    }
                    else
                        execSay(valasz, MainActivity.this);
                });
            } catch (Exception e) {
                Log.e(TAG, "Error in LLM processing", e);
                // If there's an error, still call the onExecuteEnded to prevent UI from getting stuck
                runOnUiThread(() -> {
                    onExecuteEnded();
                });
            }
        }).start();
    }



    @Override
    public void onExecuteEnded()
    {
        speechManager.getSpeechInput();
    }

    class SpeechManager
    {
        @SuppressLint("QueryPermissionsNeeded")
        public void getSpeechInput()
        {
                Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);

                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());

                if (intent.resolveActivity(getPackageManager()) != null)
                {
                    startActivityForResult(intent, 100); // szoveg hozza
                }
            }
        }
    }
