package ru.example.service;

import org.json.JSONObject;
import org.springframework.stereotype.Service;

import java.util.Calendar;

@Service
public class TimeService {
    public JSONObject getCurrentTime() {
        Calendar calendar = Calendar.getInstance();
        return new JSONObject()
                .put("hour", calendar.get(Calendar.HOUR))
                .put("minute", calendar.get(Calendar.MINUTE))
                .put("second", calendar.get(Calendar.SECOND));
    }
}
