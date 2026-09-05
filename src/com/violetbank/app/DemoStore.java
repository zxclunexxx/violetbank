package com.violetbank.app;

import android.content.Context;
import android.content.SharedPreferences;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class DemoStore {
    static final class Operation {
        final String title;
        final String subtitle;
        final double amount;
        final boolean income;

        Operation(String title, String subtitle, double amount, boolean income) {
            this.title = title;
            this.subtitle = subtitle;
            this.amount = amount;
            this.income = income;
        }
    }

    private final SharedPreferences prefs;
    private final List<Operation> operations = new ArrayList<>();
    private double balance;

    DemoStore(Context context) {
        prefs = context.getSharedPreferences("violet_demo", Context.MODE_PRIVATE);
        balance = Double.longBitsToDouble(prefs.getLong("balance", Double.doubleToLongBits(128450.72)));
        seedOperations();
    }

    private void seedOperations() {
        operations.add(new Operation("Зарплата", "Сегодня, 09:41", 75000, true));
        operations.add(new Operation("Пятёрочка", "Сегодня, 08:15", 1842.35, false));
        operations.add(new Operation("Яндекс Go", "Вчера, 22:08", 487, false));
        operations.add(new Operation("Перевод от Анны", "Вчера, 18:34", 3500, true));
        operations.add(new Operation("Ozon", "2 сентября, 14:12", 2699, false));
        operations.add(new Operation("Кофейня", "2 сентября, 10:20", 390, false));
    }

    double getBalance() { return balance; }
    List<Operation> getOperations() { return operations; }

    boolean spend(String title, double amount) {
        if (amount <= 0 || amount > balance) return false;
        balance -= amount;
        operations.add(0, new Operation(title, "Только что", amount, false));
        save();
        return true;
    }

    void addMoney(double amount) {
        if (amount <= 0) return;
        balance += amount;
        operations.add(0, new Operation("Пополнение", "Только что", amount, true));
        save();
    }

    void reset() {
        balance = 128450.72;
        operations.clear();
        seedOperations();
        save();
    }

    private void save() {
        prefs.edit().putLong("balance", Double.doubleToRawLongBits(balance)).apply();
    }

    static String money(double value) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(new Locale("ru", "RU"));
        symbols.setGroupingSeparator(' ');
        symbols.setDecimalSeparator(',');
        DecimalFormat format = new DecimalFormat("#,##0.00", symbols);
        return format.format(value) + " ₽";
    }
}
