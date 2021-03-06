package com.keba.keba;

import android.content.Intent;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.keba.keba.addQuestion.AddQuestionActivity;
import com.keba.keba.backend.Backend;
import com.keba.keba.barcodeUtil.BarcodeIntentIntegrator;
import com.keba.keba.barcodeUtil.BarcodeIntentResult;
import com.keba.keba.data.Alarm;
import com.keba.keba.data.QR;
import com.keba.keba.data.Question;
import com.keba.keba.data.request.AlarmRequest;
import com.keba.keba.data.response.AllResponse;
import com.keba.keba.data.response.SearchResponse;
import com.keba.keba.questionList.QuestionItemClickListener;
import com.keba.keba.questionList.QuestionRecyclerViewAdapter;
import com.keba.keba.settings.SettingsActivity;
import com.keba.keba.showQuestion.ShowQuestionActivity;
import com.keba.keba.util.QuestionByDateComparator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class StartActivity extends AppCompatActivity implements QuestionItemClickListener {

    private static final String KEY_RECYCLER_VIEW_STATE = "recyclerViewState";

    @BindView(R.id.activity_start_searchEdit) EditText searchEditText;
    @BindView(R.id.activity_start_searchButton) ImageButton searchButton;
    @BindView(R.id.activity_start_qrButton) ImageButton qrButton;
    @BindView(R.id.progressBar) ProgressBar progressBar;

    private QuestionRecyclerViewAdapter recyclerViewAdapter;
    private RecyclerView.LayoutManager recyclerViewLayoutManager;
    private String qrScanStr;
    private List<Question> lastQuestions;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start);
        ButterKnife.bind(this);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayShowTitleEnabled(false);

        progressBar.setVisibility(View.VISIBLE);
        Backend.getInstance().allQuestions()
                .enqueue(new Callback<AllResponse>() {
                    int retry = 0;
                    @Override
                    public void onResponse(Call<AllResponse> call, Response<AllResponse> response) {
                        AllResponse resp = response.body();
                        if (resp != null) {
                            updateList(resp.getAllQuestions());
                        } else {
                            Toast.makeText(StartActivity.this, "There are no questions! Everyone is happy :-)", Toast.LENGTH_SHORT).show();
                        }
                        progressBar.setVisibility(View.GONE);
                    }

                    @Override
                    public void onFailure(Call<AllResponse> call, Throwable t) {
                        Log.e("StartActivity", t != null && t.getMessage() != null ? t.getMessage() : "<None>");
                        t.printStackTrace();
                        retry++;
                        if (retry < 5) {
                            call.clone().enqueue(this);
                        } else {
                            progressBar.setVisibility(View.GONE);
                            Toast.makeText(StartActivity.this, "Failed to query questions...", Toast.LENGTH_SHORT).show();
                        }
                    }
                });


        /*
        Question question = new Question();
        question.date = "26.9.2017";
        question.score = 15;
        question.tags = "Keba, Linz";
        question.title = "Will we win this contest?";
        */

        ArrayList<Question> list = new ArrayList<>();

        // set up ListAdapter and RecyclerView
        recyclerViewAdapter = new QuestionRecyclerViewAdapter(this,this);
        recyclerViewAdapter.updateList(list);
        RecyclerView recyclerView = (RecyclerView) findViewById(R.id.activity_start_listView);
        recyclerView.setAdapter(recyclerViewAdapter);
        recyclerView.setHasFixedSize(true);
        recyclerViewLayoutManager = recyclerView.getLayoutManager();


        // restore last position of the recycler view list
        Parcelable layoutManagerState = (savedInstanceState != null ? savedInstanceState.getParcelable(KEY_RECYCLER_VIEW_STATE) : null);
        if(layoutManagerState != null){
            recyclerViewLayoutManager.onRestoreInstanceState(layoutManagerState);
        }

    }

    public void updateList(List<Question> questions) {
        // sort it
        Collections.sort(questions, new QuestionByDateComparator());
        // display it
        recyclerViewAdapter.updateList(lastQuestions = questions);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_settings:
                Intent i = new Intent(this, SettingsActivity.class);
                startActivity(i);
        };
        return super.onOptionsItemSelected(item);
    }


    @OnClick(R.id.activity_start_searchButton)
    public void onClickSearchButton(View view) {

        String searchStr = searchEditText.getText().toString();
        if(!searchStr.isEmpty()) {

            // TODO Search by text

        }
    }


    @OnClick(R.id.activity_start_qrButton)
    public void onClickQrButton(View view) {
        BarcodeIntentIntegrator barcodeIntentIntegrator = new BarcodeIntentIntegrator(this);
        barcodeIntentIntegrator.initiateScan();
    }

    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);

        switch (requestCode) {
            case BarcodeIntentIntegrator.REQUEST_CODE:
                BarcodeIntentResult scanResult = BarcodeIntentIntegrator.parseActivityResult(requestCode, resultCode, intent);
                if (scanResult != null) {
                    if(!scanResult.getContents().equals("null")){
                        qrScanStr = scanResult.getContents();
                        // TODO was soll passieren, wenn ein qr code gelesen wurde?
                        QR qr = null;
                        if (qrScanStr != null) {
                            Gson gson = new GsonBuilder().create();
                            qr = gson.fromJson(qrScanStr, QR.class);
                        }

                        progressBar.setVisibility(View.VISIBLE);
                        Backend.getInstance().queryByQR(new AlarmRequest("en", qr))
                                .enqueue(new Callback<SearchResponse>() {
                                    int retry = 0;
                                    @Override
                                    public void onResponse(Call<SearchResponse> call, Response<SearchResponse> response) {
                                        SearchResponse resp = response.body();
                                        if (resp != null && resp.questions != null) {
                                            progressBar.setVisibility(View.GONE);
                                            updateList(resp.questions);
                                        }
                                    }

                                    @Override
                                    public void onFailure(Call<SearchResponse> call, Throwable t) {
                                        t.printStackTrace();
                                        retry++;
                                        if (retry < 5) {
                                            call.clone().enqueue(this);
                                        } else {
                                            progressBar.setVisibility(View.GONE);
                                            Toast.makeText(StartActivity.this, "Failed to query questions...", Toast.LENGTH_SHORT).show();
                                        }
                                    }
                                });
                    }
                }
                break;
        }

    }


    @OnClick(R.id.activity_start_add)
    public void onClickAddButton(View view) {
        Intent intent = new Intent(this, AddQuestionActivity.class);
        startActivity(intent);
    }


    @Override public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (recyclerViewLayoutManager != null) {
            outState.putParcelable(KEY_RECYCLER_VIEW_STATE, recyclerViewLayoutManager.onSaveInstanceState());
        }
    }

    @Override public void onItemClick(long id) {
        Intent intent = new Intent(this, ShowQuestionActivity.class);

        if (lastQuestions != null && id >= 0 && id < lastQuestions.size() ) {
            Question question = lastQuestions.get((int) id);
            intent.putExtra(ShowQuestionActivity.KEY_QUESTION_ID, question);
            startActivity(intent);
        }
    }
}
