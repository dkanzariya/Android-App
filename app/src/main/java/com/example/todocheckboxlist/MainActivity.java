package com.example.todocheckboxlist;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private static final String PREFS_NAME = "TodoPrefs";
    private static final String TODO_KEY = "TodoList";
    private final List<String> todos = new ArrayList<>();
    private LinearLayout todoContainer;
    private EditText todoInput;
    private TextView emptyMessage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        todoInput = findViewById(R.id.todoInput);
        todoContainer = findViewById(R.id.todoContainer);
        emptyMessage = findViewById(R.id.emptyMessage);
        Button addButton = findViewById(R.id.addButton);

        addButton.setOnClickListener(view -> addTodo());
        todoInput.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                addTodo();
                return true;
            }
            return false;
        });

        loadTodos();
        renderTodos();
    }

    private void saveTodos() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        JSONArray jsonArray = new JSONArray(todos);
        editor.putString(TODO_KEY, jsonArray.toString());
        editor.apply();
    }

    private void loadTodos() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String json = prefs.getString(TODO_KEY, null);
        if (json != null) {
            try {
                JSONArray jsonArray = new JSONArray(json);
                todos.clear();
                for (int i = 0; i < jsonArray.length(); i++) {
                    todos.add(jsonArray.getString(i));
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    private void addTodo() {
        String text = todoInput.getText().toString().trim();
        if (text.isEmpty()) {
            todoInput.setError(getString(R.string.empty_todo_error));
            return;
        }

        todos.add(text);
        saveTodos();
        todoInput.setText("");
        renderTodos();
    }

    private void renderTodos() {
        todoContainer.removeAllViews();
        emptyMessage.setVisibility(todos.isEmpty() ? TextView.VISIBLE : TextView.GONE);

        for (String todo : todos) {
            CheckBox checkBox = new CheckBox(this);
            checkBox.setText(todo);
            checkBox.setTextColor(getColor(R.color.text_primary));
            checkBox.setTextSize(18);
            checkBox.setPadding(0, 12, 0, 12);
            checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    todos.remove(todo);
                    saveTodos();
                    renderTodos();
                }
            });
            todoContainer.addView(checkBox);
        }
    }
}
