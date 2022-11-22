package net.fabricmc.bosch.parsing;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class Polynomial {
    private final ArrayList<Float> coefficients;
    public Polynomial (float... args) {
        coefficients = new ArrayList<>();

        for(float f : args) {
            coefficients.add(f);
        }

        while (coefficients.get(coefficients.size() - 1) == 0.0f && coefficients.size() > 1) {
            coefficients.remove(coefficients.size() - 1);
        }
    }

    public Polynomial() {
        coefficients = new ArrayList<>();
        coefficients.add(0.0f);
    }

    public Polynomial (ArrayList<Float> args) {
        coefficients = args;
        if (coefficients.isEmpty()) coefficients.add(0.0f);
        while (coefficients.get(coefficients.size() - 1) == 0.0f && coefficients.size() > 1) {
            coefficients.remove(coefficients.size() - 1);
        }
    }

    public int degree() { return coefficients.size() - 1; }

    public float getCoefficient(int c) { return c < coefficients.size() ? coefficients.get(c) : 0.0f; }

    public Polynomial add(@NotNull Polynomial p) {
        ArrayList<Float> newCoefficients = new ArrayList<>();
        for(int i = 0; i <= Math.max(degree(), p.degree()); i++) {
            newCoefficients.add(getCoefficient(i) + p.getCoefficient(i));
        }
        return new Polynomial(newCoefficients);
    }

    public Polynomial subtract(@NotNull Polynomial p) {
        ArrayList<Float> newCoefficients = new ArrayList<>();
        for(int i = 0; i <= Math.max(degree(), p.degree()); i++) {
            newCoefficients.add(getCoefficient(i) - p.getCoefficient(i));
        }
        return new Polynomial(newCoefficients);
    }

    public Polynomial multiply(float f) {
        ArrayList<Float> newCoefficients = new ArrayList<>();
        for (float coefficient : coefficients) { newCoefficients.add(coefficient * f); }
        return new Polynomial(newCoefficients);
    }

    public Polynomial multiply(@NotNull Polynomial p) {
        ArrayList<Float> newCoefficients = new ArrayList<>();
        for (int i = 0; i <= degree(); i++) {
            for (int j = 0; j <= p.degree(); j++) {
                if (i + j == newCoefficients.size()) newCoefficients.add(0.0f);

                newCoefficients.set(i + j, newCoefficients.get(i + j) + getCoefficient(i) * p.getCoefficient(j));
            }
        }

        return new Polynomial(newCoefficients);
    }

    public Polynomial derivative(int n) {
        if (n == 0) return new Polynomial(coefficients);
        if (n < 0) return integrate(-n);

        ArrayList<Float> newCoefficients = new ArrayList<>();
        for (int i = 1; i < coefficients.size(); i++) {
            newCoefficients.add(i * coefficients.get(i));
        }
        return new Polynomial(newCoefficients).derivative(n - 1);
    }

    public Polynomial integrate(int n) {
        if (n == 0) return new Polynomial(coefficients);
        if (n < 0) return derivative(-n);

        ArrayList<Float> newCoefficients = new ArrayList<>();
        newCoefficients.add(0.0f);
        for (int i = 1; i < coefficients.size(); i++) {
            newCoefficients.add(coefficients.get(i) / (i + 1));
        }
        return new Polynomial(newCoefficients).integrate(n - 1);
    }

    public float eval(float f) {
        float accumulator = 0;
        float fPow = 1;
        for(float coefficient : coefficients) {
            accumulator += coefficient * fPow;
            fPow *= f;
        }
        return accumulator;
    }

    public String toString() {
        StringBuilder s = new StringBuilder("{ " + coefficients.get(0));

        for(int i = 1; i < coefficients.size(); i++) {
            float c = getCoefficient(i);
            if (c != 0.0f) {
                s.append(getCoefficient(i) >= 0 ? " + " : " - ").append(Math.abs(c)).append("*x^").append(i);
            }
        }

        return s + " }";
    }

    public static Polynomial lagrange(@NotNull List<Float> x, @NotNull List<Float> y) {
        Polynomial L = new Polynomial();

        for (int i = 0; i < x.size(); i++) {
            Polynomial Li = new Polynomial(1.0f);

            for (int j = 0; j < x.size(); j++) {
                if (i == j) continue;
                float scale = (x.get(i) - x.get(j));
                Li = Li.multiply(new Polynomial(-x.get(j)/scale, 1/scale));
            }

            L = L.add(Li.multiply(y.get(i)));
        }

        return L;
    }

}
