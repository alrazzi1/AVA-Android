package com.example.CoulterGlassesDebug;

import java.math.RoundingMode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class Helper {
    public static double roundToDecimalPlaces(double number, int decimalPlaces) {
        if(decimalPlaces < 0){
            return number;
        }
        BigDecimal bd = new BigDecimal(number).setScale(decimalPlaces, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }

    public static List<Double> roundToDecimalPlaces(List<Double> numbers, int decimalPlaces){
        List<Double> roundedNumbers = new ArrayList<Double>();
        for(Double number : numbers){
            roundedNumbers.add(roundToDecimalPlaces(number, decimalPlaces));
        }
        return roundedNumbers;
    }

    public static double reverseLinearMap(double input, double yMin, double yMax, double xMin, double xMax) {
        // Ensure input is within the expected range [0, 180]
        if (input < 0) input = xMin;
        if (input > 180) input = xMax;

        // Calculate the mapped value
        double output = yMax - (input - xMin) * (yMax - yMin) / (xMax - xMin);

        return output;
    }

    public static double radiansToDegrees(double radians){
        return radians*57.29577951308232;
    }

    public static List<Double> radiansToDegrees(List<Double> radians){
        List<Double> degrees = new ArrayList<Double>();
        for(Double radian : radians){
            degrees.add(radiansToDegrees(radian));
        }
        return degrees;
    }
}
