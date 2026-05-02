package com.student.foodcontrol;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LayoutAnimationController;
import android.animation.LayoutTransition;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends Activity {
    static final String PREFS = "food_control_state";
    static final String KEY_STATE = "state_json";
    static final String KEY_LAST_WARNING = "last_warning_key";
    static final String CHANNEL_ID = "food_control_alerts";
    static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");
    static final long DAY_MS = 24L * 60L * 60L * 1000L;
    static final long HOUR_MS = 60L * 60L * 1000L;

    static final int BG = Color.rgb(14, 17, 23);
    static final int CARD = Color.rgb(23, 27, 36);
    static final int CARD_2 = Color.rgb(31, 37, 49);
    static final int LINE = Color.rgb(48, 56, 70);
    static final int TEXT = Color.rgb(244, 247, 251);
    static final int MUTED = Color.rgb(150, 161, 178);
    static final int ACCENT = Color.rgb(126, 231, 135);
    static final int WARN = Color.rgb(255, 214, 102);
    static final int BAD = Color.rgb(255, 107, 107);

    FrameLayout root;
    ScrollView scrollView;
    LinearLayout content;
    Button editButton;
    AppState state;

    boolean editMode = false;
    boolean stockInfo = false;
    boolean newProductOpen = false;
    DraftProduct draft = null;
    String pendingImageProductId = null;

    final Set<String> mealInfo = new HashSet<>();
    final Set<String> cardInfo = new HashSet<>();
    final Set<String> rowEdit = new HashSet<>();
    final Set<String> addStockOpen = new HashSet<>();
    final Map<String, String> addMealSelected = new HashMap<>();
    final Set<String> addMealOpen = new HashSet<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        createNotificationChannel(this);
        requestNotificationPermissionIfNeeded();
        state = loadState(this);
        resetIfNeeded();
        scheduleDailyCheck(this);
        root = new FrameLayout(this);
        setContentView(root);
        render();
    }

    void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 33);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 77 && resultCode == RESULT_OK && data != null && data.getData() != null && pendingImageProductId != null) {
            Uri uri = data.getData();
            try {
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) {
            }
            Product p = state.product(pendingImageProductId);
            if (p != null) {
                p.image = uri.toString();
                saveState(this, state);
            }
            pendingImageProductId = null;
            render();
        }
    }

    void render() {
        int y = scrollView == null ? 0 : scrollView.getScrollY();
        root.removeAllViews();

        scrollView = new ScrollView(this);
        scrollView.setFillViewport(false);
        scrollView.setBackgroundColor(BG);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(12), dp(12), dp(12), dp(94));
        content.setLayoutTransition(new LayoutTransition());
        scrollView.addView(content, new ScrollView.LayoutParams(-1, -2));
        root.addView(scrollView, new FrameLayout.LayoutParams(-1, -1));

        content.addView(rationScreen());
        content.addView(space(14));
        content.addView(stockScreen());

        editButton = new Button(this);
        editButton.setText(editMode ? "Подтвердить изменения" : "Изменить рацион");
        editButton.setTextColor(Color.rgb(7, 17, 11));
        editButton.setTypeface(Typeface.DEFAULT_BOLD);
        editButton.setAllCaps(false);
        editButton.setBackground(round(ACCENT, dp(24), 0));
        editButton.setOnClickListener(v -> {
            editMode = !editMode;
            rowEdit.clear();
            addMealOpen.clear();
            addMealSelected.clear();
            addStockOpen.clear();
            newProductOpen = false;
            animateTap(v);
            render();
        });
        FrameLayout.LayoutParams fp = new FrameLayout.LayoutParams(-1, dp(58), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        fp.setMargins(dp(16), 0, dp(16), dp(18));
        root.addView(editButton, fp);
        scrollView.post(() -> scrollView.setScrollY(y));
    }

    View rationScreen() {
        LinearLayout box = screenBox();
        LinearLayout top = row(Gravity.CENTER_VERTICAL);
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        TextView title = title("Рацион");
        TextView sub = small("Склад расположен ниже: пролистни экран вниз.");
        titles.addView(title);
        titles.addView(sub);
        top.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));
        Button notify = smallButton("🔔");
        notify.setOnClickListener(v -> {
            requestNotificationPermissionIfNeeded();
            sendStockWarnings(this);
            toast("Уведомления проверены. Ежедневная проверка включена.");
        });
        top.addView(notify, new LinearLayout.LayoutParams(dp(48), dp(44)));
        box.addView(top);
        box.addView(space(10));
        box.addView(dailyCoverageView());
        box.addView(space(12));
        for (Meal m : state.meals) {
            box.addView(mealCard(m));
            box.addView(space(12));
        }
        return box;
    }

    View dailyCoverageView() {
        Totals plan = calcAll(false);
        Totals actual = calcAll(true);
        LinearLayout outer = cardBox();
        LinearLayout head = row(Gravity.CENTER_VERTICAL);
        TextView label = text("Закрыто", 20, ACCENT, true);
        head.addView(label);
        outer.addView(head);
        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setPadding(0, dp(8), 0, dp(2));
        for (Nutrient n : nutrients()) {
            double p = plan.n.getOrDefault(n.key, 0.0);
            double a = actual.n.getOrDefault(n.key, 0.0);
            String txt;
            int color;
            if (p + 1e-9 >= n.target) {
                if (a + 1e-9 >= n.target) {
                    txt = n.shortName + " ✓";
                    color = ACCENT;
                } else {
                    txt = n.shortName + " ✓";
                    color = MUTED;
                }
            } else {
                txt = n.shortName + ": не закрыто " + fmt(n.target - p) + " " + n.unit;
                color = MUTED;
            }
            TextView chip = text(txt, 13, color, true);
            chip.setPadding(dp(10), dp(7), dp(10), dp(7));
            chip.setBackground(round(CARD_2, dp(16), LINE));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.setMargins(0, 0, dp(8), 0);
            chips.addView(chip, lp);
        }
        hsv.addView(chips);
        outer.addView(hsv);
        return outer;
    }

    View mealCard(Meal meal) {
        LinearLayout card = cardBox();
        card.setPadding(dp(12), dp(12), dp(12), dp(12));
        boolean info = mealInfo.contains(meal.id);
        LinearLayout head = row(Gravity.CENTER_VERTICAL);
        if (info) {
            HorizontalScrollView hsv = new HorizontalScrollView(this);
            hsv.setHorizontalScrollBarEnabled(false);
            Totals t = calcRows(meal.rows, false);
            TextView line = text("БЖУ: " + totalsLine(t, true, false), 15, TEXT, true);
            line.setSingleLine(true);
            hsv.addView(line);
            head.addView(hsv, new LinearLayout.LayoutParams(0, -2, 1));
            Button back = smallButton("↩");
            back.setOnClickListener(v -> {
                mealInfo.remove(meal.id);
                animateTap(v);
                render();
            });
            head.addView(back, new LinearLayout.LayoutParams(dp(44), dp(40)));
        } else {
            TextView name = text(meal.name, 18, TEXT, true);
            head.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
            if (!editMode) {
                Button eatMeal = smallButton(mealAllEaten(meal) ? "Отменить" : "Съел приём");
                eatMeal.setOnClickListener(v -> {
                    animateTap(v);
                    toggleMealEaten(meal);
                });
                head.addView(eatMeal, new LinearLayout.LayoutParams(dp(112), dp(40)));
            }
            Button right = smallButton(editMode ? "+" : "i");
            right.setOnClickListener(v -> {
                animateTap(v);
                if (editMode) {
                    if (addMealOpen.contains(meal.id)) {
                        addMealOpen.remove(meal.id);
                        addMealSelected.remove(meal.id);
                    } else {
                        addMealOpen.add(meal.id);
                    }
                } else {
                    mealInfo.add(meal.id);
                }
                render();
            });
            head.addView(right, new LinearLayout.LayoutParams(dp(44), dp(40)));
        }
        card.addView(head);
        card.addView(space(8));

        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setLayoutTransition(new LayoutTransition());
        rows.setOnDragListener((v, event) -> handleDropOnMeal(event, meal.id, null));
        if (meal.rows.isEmpty()) {
            TextView empty = small("В этом приёме пищи пока нет продуктов.");
            empty.setPadding(dp(8), dp(10), dp(8), dp(10));
            rows.addView(empty);
        } else {
            for (RowItem r : meal.rows) {
                rows.addView(info ? infoRow(r) : foodRow(meal, r));
                rows.addView(space(6));
            }
        }
        card.addView(rows);
        if (editMode && addMealOpen.contains(meal.id)) {
            card.addView(addMealPanel(meal));
        }
        return card;
    }

    View foodRow(Meal meal, RowItem row) {
        if (rowEdit.contains(row.id)) return editRow(meal, row);
        Product p = state.product(row.productId);
        LinearLayout r = rowBox();
        r.setOnLongClickListener(v -> startRowDrag(v, row.id));
        r.setOnDragListener((v, event) -> handleDropOnMeal(event, meal.id, row.id));
        r.addView(productIcon(p, dp(42), false), new LinearLayout.LayoutParams(dp(42), dp(42)));
        LinearLayout mid = new LinearLayout(this);
        mid.setOrientation(LinearLayout.VERTICAL);
        TextView name = text(p == null ? "Продукт удалён" : p.name, 15, TEXT, true);
        TextView qty = small(formatRowQty(row, p));
        mid.addView(name);
        mid.addView(qty);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(0, -2, 1);
        mlp.setMargins(dp(10), 0, dp(8), 0);
        r.addView(mid, mlp);
        if (editMode) {
            Button edit = smallButton("✎");
            edit.setOnClickListener(v -> {
                rowEdit.add(row.id);
                animateTap(v);
                render();
            });
            r.addView(edit, new LinearLayout.LayoutParams(dp(50), dp(40)));
        } else {
            boolean ok = row.eaten || hasEnough(row);
            if (!ok) {
                TextView bad = text("!", 18, BAD, true);
                bad.setGravity(Gravity.CENTER);
                bad.setBackground(round(Color.rgb(58, 30, 34), dp(18), BAD));
                r.addView(bad, new LinearLayout.LayoutParams(dp(42), dp(38)));
            } else {
                Button eat = smallButton(row.eaten ? "✓" : "Съел");
                eat.setTextColor(row.eaten ? ACCENT : TEXT);
                eat.setOnClickListener(v -> {
                    animateTap(v);
                    toggleRowEaten(row);
                });
                r.addView(eat, new LinearLayout.LayoutParams(dp(58), dp(40)));
            }
        }
        return r;
    }

    View editRow(Meal meal, RowItem row) {
        Product p = state.product(row.productId);
        LinearLayout box = rowBox();
        box.addView(productIcon(p, dp(42), false), new LinearLayout.LayoutParams(dp(42), dp(42)));
        EditText input = input(formatEditValue(row), "0 удалить; <5 шт; 5+ гр");
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(0, dp(46), 1);
        ilp.setMargins(dp(10), 0, dp(8), 0);
        box.addView(input, ilp);
        Button ok = smallButton("✓");
        ok.setTextColor(ACCENT);
        ok.setOnClickListener(v -> {
            double val = parseNumber(input.getText().toString());
            if (!Double.isFinite(val)) {
                toast("Введите число.");
                return;
            }
            if (val <= 0) {
                if (row.eaten) row.nextDelete = true;
                else meal.rows.remove(row);
            } else {
                String unit = inferUnit(val);
                if (row.eaten) {
                    row.nextQty = val;
                    row.nextUnit = unit;
                    row.nextDelete = false;
                } else {
                    row.qty = val;
                    row.unit = unit;
                }
            }
            rowEdit.remove(row.id);
            saveState(this, state);
            animateTap(v);
            render();
        });
        box.addView(ok, new LinearLayout.LayoutParams(dp(50), dp(42)));
        return box;
    }

    View infoRow(RowItem row) {
        Product p = state.product(row.productId);
        LinearLayout outer = rowBox();
        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        Totals t = new Totals();
        if (p != null) addToTotals(t, p, row.qty, row.unit);
        TextView line = text(totalsLine(t, false, true), 14, MUTED, false);
        line.setSingleLine(true);
        hsv.addView(line);
        outer.addView(hsv, new LinearLayout.LayoutParams(-1, -2));
        return outer;
    }

    View addMealPanel(Meal meal) {
        LinearLayout panel = cardBox();
        panel.setBackground(round(Color.rgb(18, 25, 32), dp(18), LINE));
        panel.setPadding(dp(10), dp(10), dp(10), dp(10));
        String selected = addMealSelected.get(meal.id);
        if (selected == null) {
            TextView hint = small("Выбери продукт из склада. «Нет» закрывает панель без добавления.");
            panel.addView(hint);
            panel.addView(space(8));
            HorizontalScrollView hsv = new HorizontalScrollView(this);
            hsv.setHorizontalScrollBarEnabled(true);
            LinearLayout list = new LinearLayout(this);
            list.setOrientation(LinearLayout.HORIZONTAL);
            list.addView(productPickCard("none", "∅", "Нет", meal.id));
            for (Product p : state.products) list.addView(productPickCard(p.id, p.icon, p.name, meal.id));
            hsv.addView(list);
            panel.addView(hsv, new LinearLayout.LayoutParams(-1, -2));
        } else {
            Product p = state.product(selected);
            TextView hint = small("Добавление: " + (p == null ? "" : p.name) + ". Число меньше 5 — штуки, 5 и больше — граммы.");
            panel.addView(hint);
            panel.addView(space(8));
            LinearLayout line = row(Gravity.CENTER_VERTICAL);
            EditText qty = input("", "например: 2 или 120");
            line.addView(qty, new LinearLayout.LayoutParams(0, dp(48), 1));
            Button ok = smallButton("✓");
            ok.setTextColor(ACCENT);
            ok.setOnClickListener(v -> {
                double q = parseNumber(qty.getText().toString());
                if (!Double.isFinite(q) || q <= 0) {
                    toast("Введите количество нового продукта.");
                    return;
                }
                meal.rows.add(new RowItem(id("row"), selected, q, inferUnit(q)));
                addMealOpen.remove(meal.id);
                addMealSelected.remove(meal.id);
                saveState(this, state);
                animateTap(v);
                render();
            });
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(dp(52), dp(46));
            blp.setMargins(dp(8), 0, 0, 0);
            line.addView(ok, blp);
            panel.addView(line);
        }
        return panel;
    }

    View productPickCard(String id, String icon, String name, String mealId) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER);
        c.setPadding(dp(8), dp(8), dp(8), dp(8));
        c.setBackground(round(CARD_2, dp(16), LINE));
        TextView i = text(icon == null ? "?" : icon, 26, TEXT, false);
        i.setGravity(Gravity.CENTER);
        TextView n = text(name, 12, TEXT, true);
        n.setGravity(Gravity.CENTER);
        n.setMaxLines(2);
        Button ok = smallButton("OK");
        ok.setOnClickListener(v -> {
            if ("none".equals(id)) {
                addMealOpen.remove(mealId);
                addMealSelected.remove(mealId);
            } else {
                addMealSelected.put(mealId, id);
            }
            animateTap(v);
            render();
        });
        c.addView(i, new LinearLayout.LayoutParams(-1, dp(34)));
        c.addView(n, new LinearLayout.LayoutParams(-1, dp(42)));
        c.addView(ok, new LinearLayout.LayoutParams(-1, dp(36)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(112), dp(126));
        lp.setMargins(0, 0, dp(8), 0);
        c.setLayoutParams(lp);
        return c;
    }

    View stockScreen() {
        LinearLayout box = screenBox();
        LinearLayout top = row(Gravity.CENTER_VERTICAL);
        TextView title = title("Склад");
        top.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        Button right = smallButton(editMode ? "+" : (stockInfo ? "↩" : "i"));
        right.setOnClickListener(v -> {
            animateTap(v);
            if (editMode) {
                newProductOpen = !newProductOpen;
                if (newProductOpen && draft == null) draft = new DraftProduct();
            } else {
                stockInfo = !stockInfo;
            }
            render();
        });
        top.addView(right, new LinearLayout.LayoutParams(dp(44), dp(40)));
        box.addView(top);
        box.addView(space(10));
        if (stockInfo) {
            box.addView(stockInfoView());
        } else {
            if (editMode && newProductOpen) {
                box.addView(newProductBuilder());
                box.addView(space(12));
            }
            List<Product> products = state.products;
            for (int i = 0; i < products.size(); i += 2) {
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.TOP);
                Product p1 = products.get(i);
                row.addView(stockCard(p1), new LinearLayout.LayoutParams(0, -2, 1));
                if (i + 1 < products.size()) {
                    LinearLayout.LayoutParams gapLp = new LinearLayout.LayoutParams(dp(10), 1);
                    row.addView(space(1), gapLp);
                    Product p2 = products.get(i + 1);
                    row.addView(stockCard(p2), new LinearLayout.LayoutParams(0, -2, 1));
                } else {
                    row.addView(space(1), new LinearLayout.LayoutParams(0, 1, 1));
                }
                box.addView(row);
                box.addView(space(10));
            }
        }
        return box;
    }

    View stockInfoView() {
        LinearLayout out = new LinearLayout(this);
        out.setOrientation(LinearLayout.VERTICAL);
        out.addView(text("Полных рационов хватает на: " + coverageDays(), 17, TEXT, true));
        out.addView(space(10));
        Map<String, Double> use = usagePerDay();
        for (Product p : state.products) {
            double need = use.getOrDefault(p.id, 0.0);
            if (need <= 1e-9) continue;
            double days = stockQty(p) / need;
            LinearLayout thin = row(Gravity.CENTER_VERTICAL);
            thin.setPadding(dp(8), dp(7), dp(8), dp(7));
            thin.setBackground(round(CARD, dp(14), LINE));
            thin.addView(productIcon(p, dp(36), false), new LinearLayout.LayoutParams(dp(36), dp(36)));
            LinearLayout mid = new LinearLayout(this);
            mid.setOrientation(LinearLayout.VERTICAL);
            TextView name = text(p.name, 14, TEXT, true);
            int color = days >= 4 ? ACCENT : (days >= 2 ? WARN : BAD);
            TextView d = text(fmt(Math.floor(days + 1e-9)) + " д", 13, color, true);
            mid.addView(name);
            mid.addView(d);
            LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(0, -2, 1);
            mlp.setMargins(dp(8), 0, dp(6), 0);
            thin.addView(mid, mlp);
            int buy = state.buyDays.getOrDefault(p.id, 3);
            Button b = smallButton(buy + " д: " + formatStockAmount(p, need * buy));
            b.setOnClickListener(v -> {
                int cur = state.buyDays.getOrDefault(p.id, 3);
                int next = cur == 3 ? 5 : (cur == 5 ? 7 : 3);
                state.buyDays.put(p.id, next);
                saveState(this, state);
                animateTap(v);
                render();
            });
            thin.addView(b, new LinearLayout.LayoutParams(dp(136), dp(40)));
            out.addView(thin);
            out.addView(space(7));
        }
        return out;
    }

    View stockCard(Product p) {
        LinearLayout c = cardBox();
        c.setPadding(dp(10), dp(10), dp(10), dp(10));
        boolean info = cardInfo.contains(p.id);
        LinearLayout top = row(Gravity.CENTER_VERTICAL);
        TextView spacer = new TextView(this);
        top.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1));
        Button ib = smallButton(info ? "↩" : "i");
        ib.setOnClickListener(v -> {
            if (info) cardInfo.remove(p.id); else cardInfo.add(p.id);
            animateTap(v);
            render();
        });
        top.addView(ib, new LinearLayout.LayoutParams(dp(40), dp(36)));
        c.addView(top);
        if (info) {
            HorizontalScrollView hsv = new HorizontalScrollView(this);
            hsv.setHorizontalScrollBarEnabled(true);
            Totals t = new Totals();
            if ("unit".equals(p.base)) addToTotals(t, p, 1, "pcs");
            else addToTotals(t, p, 100, "g");
            String prefix = "unit".equals(p.base) ? "На 1 шт: " : "На 100 г: ";
            TextView data = text(prefix + totalsLine(t, true, true), 13, MUTED, false);
            data.setSingleLine(true);
            hsv.addView(data);
            c.addView(hsv, new LinearLayout.LayoutParams(-1, dp(48)));
            return c;
        }
        View icon = productIcon(p, dp(72), true);
        icon.setOnClickListener(v -> {
            if (editMode) openImagePicker(p.id);
        });
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(72), dp(72));
        ilp.gravity = Gravity.CENTER_HORIZONTAL;
        c.addView(icon, ilp);
        TextView name = text(p.name, 14, TEXT, true);
        name.setGravity(Gravity.CENTER);
        name.setMaxLines(3);
        c.addView(name, new LinearLayout.LayoutParams(-1, -2));
        TextView qty = text(formatStockAmount(p, stockQty(p)), 14, ACCENT, true);
        qty.setGravity(Gravity.CENTER);
        c.addView(qty, new LinearLayout.LayoutParams(-1, -2));
        Exp e = expInfo(p);
        TextView exp = text(e.text, 13, e.urgent ? BAD : MUTED, true);
        exp.setGravity(Gravity.CENTER);
        c.addView(exp, new LinearLayout.LayoutParams(-1, -2));
        c.addView(space(6));
        if (addStockOpen.contains(p.id)) {
            c.addView(addStockPanel(p));
        } else {
            Button add = smallButton("Добавить");
            add.setOnClickListener(v -> {
                addStockOpen.add(p.id);
                animateTap(v);
                render();
            });
            c.addView(add, new LinearLayout.LayoutParams(-1, dp(42)));
        }
        return c;
    }

    View addStockPanel(Product p) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        EditText qty = input("", "-200 гр, 500 гр, -2 шт, 1 уп");
        EditText exp = input("", "срок: дд.мм.гг; при минусе не нужен");
        LinearLayout buttons = row(Gravity.CENTER_VERTICAL);
        Button cancel = smallButton("×");
        cancel.setOnClickListener(v -> {
            addStockOpen.remove(p.id);
            animateTap(v);
            render();
        });
        Button ok = smallButton("✓");
        ok.setTextColor(ACCENT);
        ok.setOnClickListener(v -> {
            Qty q = parseQty(qty.getText().toString(), p.stockUnit);
            if (q == null || Math.abs(q.value) < 1e-9) {
                toast("Введите количество. Минус разрешён.");
                return;
            }
            double stockAmount = normalizeToStockUnit(p, q.value, q.unit);
            if (stockAmount < 0) {
                boolean done = removeStock(p, -stockAmount);
                if (!done) {
                    toast("Нельзя вычесть больше, чем есть на складе.");
                    return;
                }
            } else {
                String date = parseDate(exp.getText().toString());
                if (date == null) date = todayMoscow().plusDays(Math.max(1, p.shelfDays)).toString();
                p.batches.add(new Batch(id("b"), stockAmount, date));
            }
            sortBatches(p);
            addStockOpen.remove(p.id);
            saveState(this, state);
            animateTap(v);
            render();
        });
        buttons.addView(cancel, new LinearLayout.LayoutParams(0, dp(40), 1));
        buttons.addView(space(8), new LinearLayout.LayoutParams(dp(8), 1));
        buttons.addView(ok, new LinearLayout.LayoutParams(0, dp(40), 1));
        box.addView(qty, new LinearLayout.LayoutParams(-1, dp(44)));
        box.addView(space(6));
        box.addView(exp, new LinearLayout.LayoutParams(-1, dp(44)));
        box.addView(space(6));
        box.addView(buttons);
        return box;
    }

    View newProductBuilder() {
        if (draft == null) draft = new DraftProduct();
        LinearLayout b = cardBox();
        b.setPadding(dp(12), dp(12), dp(12), dp(12));
        LinearLayout head = row(Gravity.CENTER_VERTICAL);
        head.addView(text("Новый продукт", 18, TEXT, true), new LinearLayout.LayoutParams(0, -2, 1));
        Button close = smallButton("×");
        close.setOnClickListener(v -> {
            newProductOpen = false;
            draft = null;
            animateTap(v);
            render();
        });
        head.addView(close, new LinearLayout.LayoutParams(dp(44), dp(38)));
        b.addView(head);
        b.addView(space(8));
        EditText name = input(draft.name, "Название");
        name.setOnFocusChangeListener((v, has) -> { if (!has) draft.name = name.getText().toString(); });
        b.addView(name, new LinearLayout.LayoutParams(-1, dp(46)));
        b.addView(space(8));
        Field f = fields().get(draft.fieldIndex);
        TextView fieldTitle = small(f.shortName + " · " + f.label + " · " + f.unit);
        b.addView(fieldTitle);
        EditText val = input(draft.getFieldValue(f), "0 можно оставить пустым");
        b.addView(val, new LinearLayout.LayoutParams(-1, dp(46)));
        LinearLayout nav = row(Gravity.CENTER_VERTICAL);
        Button prev = smallButton("←");
        Button next = smallButton("→");
        TextView idx = small((draft.fieldIndex + 1) + "/" + fields().size());
        idx.setGravity(Gravity.CENTER);
        prev.setOnClickListener(v -> {
            draft.name = name.getText().toString();
            draft.setFieldValue(f, val.getText().toString());
            draft.fieldIndex = Math.max(0, draft.fieldIndex - 1);
            animateTap(v);
            render();
        });
        next.setOnClickListener(v -> {
            draft.name = name.getText().toString();
            draft.setFieldValue(f, val.getText().toString());
            draft.fieldIndex = Math.min(fields().size() - 1, draft.fieldIndex + 1);
            animateTap(v);
            render();
        });
        nav.addView(prev, new LinearLayout.LayoutParams(0, dp(40), 1));
        nav.addView(idx, new LinearLayout.LayoutParams(0, dp(40), 1));
        nav.addView(next, new LinearLayout.LayoutParams(0, dp(40), 1));
        b.addView(nav);
        b.addView(space(8));
        EditText qty = input(draft.qty, "Количество: 500 гр или 2 шт");
        EditText exp = input(draft.exp, "Срок годности: дд.мм.гг");
        b.addView(qty, new LinearLayout.LayoutParams(-1, dp(46)));
        b.addView(space(8));
        b.addView(exp, new LinearLayout.LayoutParams(-1, dp(46)));
        b.addView(space(8));
        Button save = smallButton("Сохранить продукт");
        save.setTextColor(ACCENT);
        save.setOnClickListener(v -> {
            draft.name = name.getText().toString().trim();
            draft.setFieldValue(f, val.getText().toString());
            draft.qty = qty.getText().toString();
            draft.exp = exp.getText().toString();
            if (draft.name.isEmpty()) {
                toast("Введите название продукта.");
                return;
            }
            Qty q = parseQty(draft.qty, "g");
            if (q == null || q.value <= 0) {
                toast("Введите начальное количество.");
                return;
            }
            String unit = "pcs".equals(q.unit) ? "pcs" : "g";
            double unitG = "pcs".equals(unit) ? 1.0 : 1.0;
            String base = "pcs".equals(unit) ? "unit" : "100g";
            Product p = new Product(id("p"), draft.name, "🍽️", unit, unitG, base, 30);
            p.m.putAll(draft.m);
            p.n.putAll(draft.n);
            String date = parseDate(draft.exp);
            if (date == null) date = todayMoscow().plusDays(30).toString();
            double amount = normalizeToStockUnit(p, q.value, q.unit);
            p.batches.add(new Batch(id("b"), amount, date));
            state.products.add(p);
            newProductOpen = false;
            draft = null;
            saveState(this, state);
            animateTap(v);
            render();
        });
        b.addView(save, new LinearLayout.LayoutParams(-1, dp(44)));
        b.addView(space(6));
        b.addView(small("Если остаток задан в граммах, данные считаются на 100 г. Если в штуках — на 1 шт."));
        return b;
    }

    boolean startRowDrag(View v, String rowId) {
        ClipData data = ClipData.newPlainText("food-row", rowId);
        if (Build.VERSION.SDK_INT >= 24) {
            v.startDragAndDrop(data, new View.DragShadowBuilder(v), rowId, 0);
        } else {
            v.startDrag(data, new View.DragShadowBuilder(v), rowId, 0);
        }
        v.animate().alpha(0.55f).scaleX(0.98f).scaleY(0.98f).setDuration(120).start();
        return true;
    }

    boolean handleDropOnMeal(DragEvent event, String targetMealId, String beforeRowId) {
        if (event.getAction() == DragEvent.ACTION_DRAG_ENDED) {
            Object local = event.getLocalState();
            return local instanceof String;
        }
        if (event.getAction() != DragEvent.ACTION_DROP) return true;
        Object local = event.getLocalState();
        if (!(local instanceof String)) return false;
        String rowId = (String) local;
        moveRow(rowId, targetMealId, beforeRowId);
        saveState(this, state);
        render();
        return true;
    }

    void moveRow(String rowId, String targetMealId, String beforeRowId) {
        RowItem moving = null;
        Meal source = null;
        for (Meal m : state.meals) {
            for (RowItem r : m.rows) {
                if (r.id.equals(rowId)) {
                    moving = r;
                    source = m;
                    break;
                }
            }
            if (moving != null) break;
        }
        Meal target = state.meal(targetMealId);
        if (moving == null || source == null || target == null) return;
        source.rows.remove(moving);
        int idx = target.rows.size();
        if (beforeRowId != null) {
            for (int i = 0; i < target.rows.size(); i++) {
                if (target.rows.get(i).id.equals(beforeRowId)) {
                    idx = i;
                    break;
                }
            }
        }
        target.rows.add(Math.max(0, Math.min(idx, target.rows.size())), moving);
    }

    void toggleMealEaten(Meal meal) {
        if (mealAllEaten(meal)) {
            for (RowItem r : meal.rows) if (r.eaten) undoRow(r);
            toast("Приём пищи отменён, склад восстановлен.");
        } else {
            int ok = 0;
            int fail = 0;
            for (RowItem r : meal.rows) {
                if (r.eaten) continue;
                if (eatRow(r)) ok++; else fail++;
            }
            if (fail > 0) toast("Часть продуктов не списана: не хватает на складе.");
            else if (ok > 0) toast("Приём пищи списан со склада.");
        }
        saveState(this, state);
        render();
    }

    boolean mealAllEaten(Meal meal) {
        if (meal.rows.isEmpty()) return false;
        for (RowItem r : meal.rows) if (!r.eaten) return false;
        return true;
    }

    void toggleRowEaten(RowItem row) {
        if (row.eaten) {
            undoRow(row);
            toast("Списание отменено.");
        } else {
            if (!eatRow(row)) {
                toast("Недостаточно продукта на складе.");
                return;
            }
        }
        saveState(this, state);
        render();
    }

    boolean eatRow(RowItem row) {
        Product p = state.product(row.productId);
        if (p == null) return false;
        double need = stockNeed(p, row.qty, row.unit);
        if (stockQty(p) + 1e-9 < need) return false;
        if (!removeStock(p, need)) return false;
        row.eaten = true;
        row.consumedProductId = row.productId;
        row.consumedQty = row.qty;
        row.consumedUnit = row.unit;
        return true;
    }

    void undoRow(RowItem row) {
        if (!row.eaten) return;
        Product p = state.product(row.consumedProductId == null ? row.productId : row.consumedProductId);
        if (p != null) {
            double amount = stockNeed(p, row.consumedQty, row.consumedUnit == null ? row.unit : row.consumedUnit);
            p.batches.add(new Batch(id("b"), amount, todayMoscow().plusDays(Math.max(1, p.shelfDays)).toString()));
            sortBatches(p);
        }
        row.eaten = false;
        row.consumedProductId = null;
        row.consumedQty = 0;
        row.consumedUnit = null;
    }

    boolean hasEnough(RowItem row) {
        Product p = state.product(row.productId);
        return p != null && stockQty(p) + 1e-9 >= stockNeed(p, row.qty, row.unit);
    }

    boolean removeStock(Product p, double amount) {
        if (stockQty(p) + 1e-9 < amount) return false;
        sortBatches(p);
        double left = amount;
        for (Batch b : p.batches) {
            if (left <= 1e-9) break;
            double take = Math.min(b.qty, left);
            b.qty -= take;
            left -= take;
        }
        ArrayList<Batch> keep = new ArrayList<>();
        for (Batch b : p.batches) if (b.qty > 1e-9) keep.add(b);
        p.batches = keep;
        return true;
    }

    static void sortBatches(Product p) {
        Collections.sort(p.batches, Comparator.comparing(b -> b.exp == null ? "9999-12-31" : b.exp));
    }

    void openImagePicker(String productId) {
        pendingImageProductId = productId;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, 77);
    }

    void openNotificationSettingsIfBlocked() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()));
            toast("Разреши уведомления в настройках приложения, если Android их заблокировал.");
            startActivity(intent);
        }
    }

    void resetIfNeeded() {
        String today = todayMoscow().toString();
        if (today.equals(state.day)) return;
        applyDailyReset(state, today);
        saveState(this, state);
    }

    static void applyDailyReset(AppState s, String today) {
        for (Meal m : s.meals) {
            ArrayList<RowItem> keep = new ArrayList<>();
            for (RowItem r : m.rows) {
                if (r.nextDelete) continue;
                if (r.nextUnit != null) {
                    r.qty = r.nextQty;
                    r.unit = r.nextUnit;
                }
                r.nextDelete = false;
                r.nextQty = 0;
                r.nextUnit = null;
                r.eaten = false;
                r.consumedProductId = null;
                r.consumedQty = 0;
                r.consumedUnit = null;
                keep.add(r);
            }
            m.rows = keep;
        }
        s.day = today;
    }

    Totals calcAll(boolean actual) {
        Totals t = new Totals();
        for (Meal m : state.meals) {
            for (RowItem r : m.rows) {
                if (actual && !r.eaten) continue;
                Product p = state.product(actual && r.consumedProductId != null ? r.consumedProductId : r.productId);
                if (p == null) continue;
                double q = actual && r.eaten && r.consumedUnit != null ? r.consumedQty : r.qty;
                String u = actual && r.eaten && r.consumedUnit != null ? r.consumedUnit : r.unit;
                addToTotals(t, p, q, u);
            }
        }
        return t;
    }

    Totals calcRows(List<RowItem> rows, boolean actual) {
        Totals t = new Totals();
        for (RowItem r : rows) {
            if (actual && !r.eaten) continue;
            Product p = state.product(r.productId);
            if (p != null) addToTotals(t, p, r.qty, r.unit);
        }
        return t;
    }

    static void addToTotals(Totals t, Product p, double q, String unit) {
        double protein;
        if ("unit".equals(p.base)) {
            double units = unitsCount(p, q, unit);
            for (String k : new String[]{"P", "F", "C", "kcal"}) t.m.put(k, t.m.get(k) + p.m.getOrDefault(k, 0.0) * units);
            for (String k : p.n.keySet()) if (t.n.containsKey(k)) t.n.put(k, t.n.get(k) + p.n.get(k) * units);
            protein = p.m.getOrDefault("P", 0.0) * units;
        } else {
            double factor = grams(p, q, unit) / 100.0;
            for (String k : new String[]{"P", "F", "C", "kcal"}) t.m.put(k, t.m.get(k) + p.m.getOrDefault(k, 0.0) * factor);
            for (String k : p.n.keySet()) if (t.n.containsKey(k)) t.n.put(k, t.n.get(k) + p.n.get(k) * factor);
            protein = p.m.getOrDefault("P", 0.0) * factor;
        }
        for (String aa : aaRatios().keySet()) {
            if (p.n.containsKey(aa)) continue;
            if (!t.n.containsKey(aa)) continue;
            AaRatio ar = aaRatios().get(aa);
            t.n.put(aa, t.n.get(aa) + protein * ar.ratio);
        }
    }

    Map<String, Double> usagePerDay() {
        Map<String, Double> out = new LinkedHashMap<>();
        for (Meal m : state.meals) {
            for (RowItem r : m.rows) {
                Product p = state.product(r.productId);
                if (p == null) continue;
                double need = stockNeed(p, r.qty, r.unit);
                out.put(p.id, out.getOrDefault(p.id, 0.0) + need);
            }
        }
        return out;
    }

    int coverageDays() {
        Map<String, Double> use = usagePerDay();
        double min = Double.POSITIVE_INFINITY;
        for (String id : use.keySet()) {
            double need = use.get(id);
            if (need <= 1e-9) continue;
            Product p = state.product(id);
            if (p == null) continue;
            min = Math.min(min, stockQty(p) / need);
        }
        if (!Double.isFinite(min)) return 0;
        return (int) Math.floor(min + 1e-9);
    }

    static double stockQty(Product p) {
        double s = 0;
        for (Batch b : p.batches) s += b.qty;
        return s;
    }

    static double stockNeed(Product p, double q, String unit) {
        if ("pack".equals(unit) && p.packSize > 0) return "pcs".equals(p.stockUnit) ? q * p.packSize : grams(p, q, unit);
        if (p.stockUnit.equals(unit)) return q;
        double g = grams(p, q, unit);
        if ("g".equals(p.stockUnit)) return g;
        if ("pcs".equals(p.stockUnit)) return p.unitG > 0 ? g / p.unitG : q;
        return q;
    }

    static double normalizeToStockUnit(Product p, double q, String unit) {
        return stockNeed(p, q, unit);
    }

    static double grams(Product p, double q, String unit) {
        if ("g".equals(unit)) return q;
        if ("pack".equals(unit) && p.packSize > 0) return q * p.packSize * p.unitG;
        return q * p.unitG;
    }

    static double unitsCount(Product p, double q, String unit) {
        if ("pack".equals(unit) && p.packSize > 0) return q * p.packSize;
        if ("pcs".equals(unit)) return q;
        return p.unitG > 0 ? q / p.unitG : q;
    }

    String totalsLine(Totals t, boolean macros, boolean nonZeroOnly) {
        ArrayList<String> a = new ArrayList<>();
        if (macros) {
            a.add("Б: " + fmt(t.m.get("P")) + " г");
            a.add("Ж: " + fmt(t.m.get("F")) + " г");
            a.add("У: " + fmt(t.m.get("C")) + " г");
            a.add("ккал: " + fmt(t.m.get("kcal")));
        }
        for (Nutrient n : nutrients()) {
            double v = t.n.getOrDefault(n.key, 0.0);
            if (nonZeroOnly && Math.abs(v) < 1e-12) continue;
            a.add(n.shortName + ": " + fmt(v) + " " + n.unit);
        }
        return a.isEmpty() ? "нет ненулевых данных" : join(a, " · ");
    }

    static String join(List<String> parts, String sep) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) sb.append(sep);
            sb.append(parts.get(i));
        }
        return sb.toString();
    }

    String formatRowQty(RowItem row, Product p) {
        if ("pcs".equals(row.unit)) {
            if (p != null && p.packSize > 0) return pieceText(p, row.qty) + " = " + packText(p, row.qty);
            return pieceText(p, row.qty);
        }
        if ("pack".equals(row.unit)) return fmt(row.qty) + " " + (p != null ? p.packLabel : "уп.");
        return fmt(row.qty) + " г";
    }

    String formatEditValue(RowItem row) {
        return fmt(row.qty);
    }

    static String formatStockAmount(Product p, double q) {
        if ("pcs".equals(p.stockUnit)) {
            String out = pieceText(p, q);
            if (p.packSize > 0 && q > 0) out += " (" + packText(p, q) + ")";
            return out;
        }
        return q >= 1000 ? fmt(q / 1000.0) + " кг" : fmt(q) + " г";
    }

    static String pieceText(Product p, double q) {
        if (p.pieceForms != null && p.pieceForms.length == 3) return fmt(q) + " " + ruPlural(q, p.pieceForms[0], p.pieceForms[1], p.pieceForms[2]);
        return fmt(q) + " шт";
    }

    static String packText(Product p, double q) {
        if (p.packSize <= 0 || q <= 0) return "";
        int n = (int) Math.round(q);
        int d = p.packSize;
        String label = p.packLabel == null ? "уп." : p.packLabel;
        if (Math.abs(q - n) > 1e-9) return fmt(q / d) + " " + label;
        int whole = n / d;
        int rem = n % d;
        if (rem == 0) return whole + " " + label;
        int g = gcd(rem, d);
        String frac = (rem / g) + "/" + (d / g) + " " + label;
        return whole > 0 ? whole + " " + frac : frac;
    }

    static int gcd(int a, int b) {
        a = Math.abs(a);
        b = Math.abs(b);
        while (b != 0) {
            int t = a % b;
            a = b;
            b = t;
        }
        return a == 0 ? 1 : a;
    }

    static String ruPlural(double n, String a, String b, String c) {
        int x = Math.abs((int) Math.floor(n)) % 100;
        int y = x % 10;
        if (x > 10 && x < 20) return c;
        if (y == 1) return a;
        if (y >= 2 && y <= 4) return b;
        return c;
    }

    static String inferUnit(double v) {
        return v > 0 && v < 5 ? "pcs" : "g";
    }

    static double parseNumber(String s) {
        try {
            return Double.parseDouble(s.trim().replace(',', '.'));
        } catch (Exception e) {
            return Double.NaN;
        }
    }

    static Qty parseQty(String raw, String fallback) {
        if (raw == null) return null;
        String s = raw.trim().toLowerCase(Locale.ROOT).replace('−', '-').replace('–', '-').replace('—', '-').replace(',', '.');
        if (s.isEmpty()) return null;
        String[] parts = s.split("\\s+", 2);
        double v;
        try {
            v = Double.parseDouble(parts[0]);
        } catch (Exception e) {
            return null;
        }
        String u = parts.length > 1 ? parts[1].trim() : fallback;
        if (u.contains("уп")) return new Qty(v, "pack");
        if (u.contains("шт") || u.contains("pcs") || u.contains("ломт")) return new Qty(v, "pcs");
        return new Qty(v, "g");
    }

    static String parseDate(String value) {
        if (value == null) return null;
        String v = value.trim();
        if (v.isEmpty()) return null;
        try {
            if (v.matches("\\d{4}-\\d{2}-\\d{2}")) return LocalDate.parse(v).toString();
            String[] p = v.split("\\.");
            if (p.length != 3) return null;
            int d = Integer.parseInt(p[0]);
            int m = Integer.parseInt(p[1]);
            int y = Integer.parseInt(p[2]);
            if (y < 100) y += 2000;
            return LocalDate.of(y, m, d).toString();
        } catch (Exception e) {
            return null;
        }
    }

    static LocalDate todayMoscow() {
        return ZonedDateTime.now(MOSCOW).toLocalDate();
    }

    static String fmt(double v) {
        if (!Double.isFinite(v)) return "0";
        double a = Math.abs(v);
        String s;
        if (a >= 100) s = String.format(Locale.US, "%.1f", v);
        else if (a >= 10) s = String.format(Locale.US, "%.2f", v);
        else if (a >= 1) s = String.format(Locale.US, "%.3f", v);
        else s = String.format(Locale.US, "%.4f", v);
        return s.replaceAll("\\.0+$", "").replaceAll("(\\.\\d*?)0+$", "$1").replaceAll("\\.$", "");
    }

    Exp expInfo(Product p) {
        return expInfoStatic(p);
    }

    static Exp expInfoStatic(Product p) {
        String e = null;
        for (Batch b : p.batches) {
            if (b.qty <= 1e-9 || b.exp == null) continue;
            if (e == null || b.exp.compareTo(e) < 0) e = b.exp;
        }
        if (e == null) return new Exp("срок не указан", false);
        try {
            LocalDate d = LocalDate.parse(e);
            ZonedDateTime end = d.atTime(23, 59, 59).atZone(MOSCOW);
            long diff = end.toInstant().toEpochMilli() - System.currentTimeMillis();
            if (diff <= 0) return new Exp("срок истёк", true);
            if (diff < DAY_MS) return new Exp(Math.max(1, (int) Math.ceil(diff / (double) HOUR_MS)) + " ч", true);
            int days = (int) Math.ceil(diff / (double) DAY_MS);
            return new Exp(days + " д", days <= 2);
        } catch (Exception ex) {
            return new Exp("срок неверный", true);
        }
    }

    static String id(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    LinearLayout screenBox() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(0, 0, 0, 0);
        return l;
    }

    LinearLayout cardBox() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackground(round(CARD, dp(20), LINE));
        l.setPadding(dp(12), dp(12), dp(12), dp(12));
        l.setLayoutTransition(new LayoutTransition());
        return l;
    }

    LinearLayout rowBox() {
        LinearLayout l = row(Gravity.CENTER_VERTICAL);
        l.setPadding(dp(8), dp(7), dp(8), dp(7));
        l.setBackground(round(CARD_2, dp(16), LINE));
        return l;
    }

    LinearLayout row(int gravity) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(gravity);
        return l;
    }

    View productIcon(Product p, int size, boolean square) {
        if (p != null && p.image != null && !p.image.isEmpty()) {
            ImageView img = new ImageView(this);
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            img.setImageURI(Uri.parse(p.image));
            img.setBackground(round(Color.rgb(35, 42, 55), square ? dp(16) : size / 2, LINE));
            img.setClipToOutline(false);
            return img;
        }
        TextView v = text(p == null ? "?" : p.icon, square ? 34 : 24, TEXT, false);
        v.setGravity(Gravity.CENTER);
        v.setBackground(round(Color.rgb(35, 42, 55), square ? dp(16) : size / 2, LINE));
        return v;
    }

    TextView title(String s) {
        return text(s, 28, TEXT, true);
    }

    TextView small(String s) {
        return text(s, 12, MUTED, false);
    }

    TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setIncludeFontPadding(true);
        return t;
    }

    Button smallButton(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setTextColor(TEXT);
        b.setTextSize(12);
        b.setAllCaps(false);
        b.setPadding(dp(4), 0, dp(4), 0);
        b.setBackground(round(Color.rgb(38, 45, 59), dp(16), LINE));
        return b;
    }

    EditText input(String value, String hint) {
        EditText e = new EditText(this);
        e.setText(value == null ? "" : value);
        e.setHint(hint);
        e.setHintTextColor(MUTED);
        e.setTextColor(TEXT);
        e.setTextSize(14);
        e.setSingleLine(true);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        e.setPadding(dp(10), 0, dp(10), 0);
        e.setBackground(round(Color.rgb(10, 14, 20), dp(14), LINE));
        return e;
    }

    View space(int h) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(h)));
        return v;
    }

    GradientDrawable round(int color, int radius, int strokeColor) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        if (strokeColor != 0) g.setStroke(dp(1), strokeColor);
        return g;
    }

    void animateTap(View v) {
        v.animate().scaleX(0.94f).scaleY(0.94f).setDuration(70).withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(90).start()).start();
    }

    void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    static void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "Food Control", NotificationManager.IMPORTANCE_DEFAULT);
            ch.setDescription("Срок годности и нехватка продуктов для рациона");
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    static void scheduleDailyCheck(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        Intent i = new Intent(context, NotificationReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(context, 1101, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        long trigger = nextMoscowTrigger(9, 0);
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP, trigger, AlarmManager.INTERVAL_DAY, pi);
    }

    static long nextMoscowTrigger(int hour, int minute) {
        ZonedDateTime now = ZonedDateTime.now(MOSCOW);
        ZonedDateTime t = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0);
        if (!t.isAfter(now)) t = t.plusDays(1);
        return t.toInstant().toEpochMilli();
    }

    static void resetIfNeededInStorage(Context context) {
        AppState s = loadState(context);
        String today = todayMoscow().toString();
        if (!today.equals(s.day)) {
            applyDailyReset(s, today);
            saveState(context, s);
        }
    }

    static void sendStockWarnings(Context context) {
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;
        AppState s = loadState(context);
        ArrayList<String> messages = buildWarningMessages(s);
        if (messages.isEmpty()) return;
        String body = join(messages.subList(0, Math.min(messages.size(), 4)), "\n");
        String key = todayMoscow() + "|" + body;
        SharedPreferences sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (key.equals(sp.getString(KEY_LAST_WARNING, ""))) return;
        sp.edit().putString(KEY_LAST_WARNING, key).apply();

        Intent open = new Intent(context, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(context, 1202, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(context, CHANNEL_ID) : new Notification.Builder(context);
        b.setSmallIcon(com.student.foodcontrol.R.drawable.ic_stat_food)
                .setContentTitle("Food Control")
                .setContentText(messages.get(0))
                .setStyle(new Notification.BigTextStyle().bigText(body))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setShowWhen(true);
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(501, b.build());
    }

    static ArrayList<String> buildWarningMessages(AppState s) {
        ArrayList<String> out = new ArrayList<>();
        int cov = coverageDaysStatic(s);
        if (cov <= 2) out.add("Рациона хватает на " + cov + " д. Нужно купить продукты.");
        for (Product p : s.products) {
            Exp e = expInfoStatic(p);
            if (e.urgent && stockQty(p) > 1e-9) out.add(p.name + ": срок годности " + e.text);
        }
        return out;
    }

    static int coverageDaysStatic(AppState s) {
        Map<String, Double> use = new LinkedHashMap<>();
        for (Meal m : s.meals) {
            for (RowItem r : m.rows) {
                Product p = s.product(r.productId);
                if (p == null) continue;
                double need = stockNeed(p, r.qty, r.unit);
                use.put(p.id, use.getOrDefault(p.id, 0.0) + need);
            }
        }
        double min = Double.POSITIVE_INFINITY;
        for (String id : use.keySet()) {
            Product p = s.product(id);
            if (p == null) continue;
            min = Math.min(min, stockQty(p) / use.get(id));
        }
        return Double.isFinite(min) ? (int) Math.floor(min + 1e-9) : 0;
    }

    static AppState loadState(Context context) {
        SharedPreferences sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String raw = sp.getString(KEY_STATE, null);
        if (raw != null) {
            try {
                AppState s = AppState.fromJson(new JSONObject(raw));
                patchState(s);
                return s;
            } catch (Exception ignored) {
            }
        }
        AppState s = defaultState();
        seed(s, 5);
        saveState(context, s);
        return s;
    }

    static void saveState(Context context, AppState s) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_STATE, s.toJson().toString()).apply();
    }

    static void patchState(AppState s) {
        LinkedHashMap<String, Product> have = new LinkedHashMap<>();
        for (Product p : s.products) have.put(p.id, p);
        for (Product p : defaultProducts()) if (!have.containsKey(p.id)) s.products.add(p);
        Product bread = s.product("bread");
        if (bread != null) {
            bread.name = "Хлеб Harry's для сэндвичей";
            bread.icon = "🍞";
            bread.stockUnit = "pcs";
            bread.unitG = 1;
            bread.base = "unit";
            bread.packSize = 12;
            bread.packLabel = "уп.";
            bread.pieceForms = new String[]{"ломтик", "ломтика", "ломтиков"};
            bread.m.put("P", 4.0);
            bread.m.put("F", 3.0);
        }
        for (Meal m : s.meals) for (RowItem r : m.rows) if ("bf_bread".equals(r.id)) { r.productId = "bread"; r.qty = 2; r.unit = "pcs"; }
        if (s.day == null || s.day.isEmpty()) s.day = todayMoscow().toString();
    }

    static AppState defaultState() {
        AppState s = new AppState();
        s.day = todayMoscow().toString();
        s.products = defaultProducts();
        s.meals = defaultMeals();
        return s;
    }

    static void seed(AppState s, int days) {
        Map<String, Double> use = new LinkedHashMap<>();
        for (Meal m : s.meals) {
            for (RowItem r : m.rows) {
                Product p = s.product(r.productId);
                if (p == null) continue;
                double need = stockNeed(p, r.qty, r.unit);
                use.put(p.id, use.getOrDefault(p.id, 0.0) + need);
            }
        }
        for (Product p : s.products) {
            double need = use.getOrDefault(p.id, 0.0);
            if (need > 0) p.batches.add(new Batch(id("b"), need * days, todayMoscow().plusDays(Math.max(1, p.shelfDays)).toString()));
        }
    }

    static ArrayList<Product> defaultProducts() {
        ArrayList<Product> ps = new ArrayList<>();
        ps.add(prod("water", "Вода", "💧", "g", 1, "100g", m(), n("Вода", .1), 365));
        ps.add(prod("tea", "Чай", "🍵", "g", 1, "100g", m(), n("Вода", .1, "Фторид (F⁻)", .2), 365));
        ps.add(prod("kefir", "Кефир", "🥛", "g", 1, "100g", m("P", 3, "F", 1, "C", 4, "kcal", 37), n("Кальций (Ca)", 120, "Фосфор (P)", 95, "Калий (K)", 150, "Натрий (Na)", 50, "Хлорид (Cl)", 80, "B2 (рибофлавин)", .17, "B12", .40, "B5 (пантотеновая кислота)", .35, "Йод (I)", 10), 7));
        ps.add(prod("flax", "Лён семена", "🌾", "g", 1, "100g", m("P", 18.3, "F", 42.2, "C", 28.9, "kcal", 569), n("α-линоленовая", 22.8, "линолевая", 5.9, "клетчатка", 27, "Магний (Mg)", 392, "Марганец (Mn)", 2.5, "Медь (Cu)", 1.2, "B1 (тиамин)", 1.64, "B6", .47), 180));
        ps.add(prod("banana", "Банан", "🍌", "g", 1, "100g", m("P", 1.1, "F", .3, "C", 22.8, "kcal", 89), n("C", 8.7, "B6", .37, "B9 (фолат)", 20, "Калий (K)", 358, "клетчатка", 2.6), 6));
        ps.add(prod("oats", "Овсяные хлопья", "🥣", "g", 1, "100g", m("P", 11.9, "F", 5.8, "C", 65.4, "kcal", 361.4), n("клетчатка", 10.1, "B1 (тиамин)", .46, "B2 (рибофлавин)", .14, "B3 (ниацин)", .96, "B5 (пантотеновая кислота)", 1.35, "B6", .12, "B7 (биотин)", 20, "B9 (фолат)", 56, "E", .4, "холин", 40, "Калий (K)", 429, "Магний (Mg)", 177, "Фосфор (P)", 523, "Железо (Fe)", 4.3, "Цинк (Zn)", 3.6, "Марганец (Mn)", 4.9, "Медь (Cu)", .6, "Молибден (Mo)", 40, "Хром (Cr)", 12), 180));
        ps.add(prod("egg", "Яйцо", "🥚", "pcs", 60, "100g", m("P", 11.5, "F", 10.5, "C", 0, "kcal", 140.5), n("холин", 294, "B12", 1.10, "B2 (рибофлавин)", .50, "B5 (пантотеновая кислота)", 1.40, "B7 (биотин)", 20, "B9 (фолат)", 47, "Селен (Se)", 30, "D", 2, "A", 140, "Йод (I)", 50, "Кальций (Ca)", 50, "Фосфор (P)", 198, "Калий (K)", 126, "Натрий (Na)", 124, "Железо (Fe)", 1.2, "Цинк (Zn)", 1.3), 21));
        ps.add(prod("cheese", "Сыр", "🧀", "g", 1, "100g", m("P", 20, "F", 28, "C", 0, "kcal", 332), n("Кальций (Ca)", 700, "Фосфор (P)", 500, "Натрий (Na)", 800, "Хлорид (Cl)", 1200, "B2 (рибофлавин)", .35, "B12", 1.5, "B5 (пантотеновая кислота)", .3, "Цинк (Zn)", 3, "Йод (I)", 30), 14));
        ps.add(prod("orange", "Апельсин", "🍊", "pcs", 150, "100g", m("P", .9, "F", .1, "C", 11.8, "kcal", 47), n("C", 53.2, "B9 (фолат)", 30, "Калий (K)", 181, "клетчатка", 2.4), 12));
        Product bread = prod("bread", "Хлеб Harry's для сэндвичей", "🍞", "pcs", 1, "unit", m("P", 4, "F", 3, "C", 0, "kcal", 0), n(), 5);
        bread.packSize = 12;
        bread.packLabel = "уп.";
        bread.pieceForms = new String[]{"ломтик", "ломтика", "ломтиков"};
        ps.add(bread);
        ps.add(prod("d3", "Витамин D3 спрей", "☀️", "pcs", 1, "unit", m(), n("D", 50), 365));
        ps.add(prod("b12", "Витамин B12 спрей", "🔴", "pcs", 1, "unit", m(), n("B12", 8.45), 365));
        ps.add(prod("chicken", "Куриное филе", "🍗", "g", 1, "100g", m("P", 19.42, "F", 4.88, "C", 0, "kcal", 121.69), n("B3 (ниацин)", 13.7, "B5 (пантотеновая кислота)", 1, "B6", .6, "B7 (биотин)", 2, "холин", 65, "Фосфор (P)", 210, "Калий (K)", 256, "Цинк (Zn)", 1, "Селен (Se)", 24, "Натрий (Na)", 60), 3));
        ps.add(prod("veg", "Овощи замороженные", "🥦", "g", 1, "100g", m("P", 3.5, "F", .6, "C", 11.3, "kcal", 65), n("A", 250, "C", 20, "K", 40, "B9 (фолат)", 50, "клетчатка", 2.5, "Калий (K)", 250), 90));
        ps.add(prod("spinach", "Шпинат", "🥬", "g", 1, "100g", m("P", 2.4, "F", 0, "C", 2.2, "kcal", 19), n("K", 483, "B9 (фолат)", 194, "A", 469, "C", 28, "Магний (Mg)", 79, "Калий (K)", 558, "Железо (Fe)", 2.7, "Медь (Cu)", .13, "Марганец (Mn)", .9), 5));
        ps.add(prod("seeds", "Семечки подсолнечника", "🌻", "g", 1, "100g", m("P", 21.5, "F", 55, "C", 5.3, "kcal", 602.2), n("E", 35.2, "линолевая", 23, "клетчатка", 8.6, "Калий (K)", 645, "Магний (Mg)", 325, "Фосфор (P)", 660, "Железо (Fe)", 5.2, "Цинк (Zn)", 5, "Медь (Cu)", 1.8, "Марганец (Mn)", 2, "Селен (Se)", 53, "B1 (тиамин)", 1.5, "B3 (ниацин)", 8.3, "B5 (пантотеновая кислота)", 1.1, "B6", 1.3, "B7 (биотин)", 7, "B9 (фолат)", 227, "холин", 55, "Хром (Cr)", 5), 180));
        ps.add(prod("oil", "Масло подсолнечное", "🛢️", "g", 1, "100g", m("P", 0, "F", 99.9, "C", 0, "kcal", 900), n("E", 41.1, "линолевая", 65), 180));
        ps.add(prod("salt", "Соль йодированная", "🧂", "g", 1, "100g", m("P", 0, "F", 0, "C", 0, "kcal", 0), n("Натрий (Na)", 39340, "Хлорид (Cl)", 60660, "Йод (I)", 1125), 365));
        ps.add(prod("lentils", "Чечевица", "🫘", "g", 1, "100g", m("P", 24, "F", 1.5, "C", 46.3, "kcal", 294.7), n("клетчатка", 10.7, "B9 (фолат)", 479, "B1 (тиамин)", .87, "B2 (рибофлавин)", .21, "B3 (ниацин)", 2.6, "B5 (пантотеновая кислота)", 1.4, "B6", .54, "B7 (биотин)", 10, "холин", 90, "Калий (K)", 955, "Магний (Mg)", 47, "Фосфор (P)", 281, "Железо (Fe)", 7.5, "Цинк (Zn)", 4.8, "Медь (Cu)", 1.3, "Марганец (Mn)", 1.9, "Молибден (Mo)", 150, "Хром (Cr)", 20), 365));
        ps.add(prod("cottage", "Творог", "🍶", "g", 1, "100g", m("P", 13.5, "F", 2.5, "C", 2, "kcal", 84.5), n("Кальций (Ca)", 110, "Фосфор (P)", 160, "Калий (K)", 112, "Натрий (Na)", 40, "Хлорид (Cl)", 60, "B2 (рибофлавин)", .25, "B12", .47, "B5 (пантотеновая кислота)", .6, "B7 (биотин)", 7, "Йод (I)", 37, "холин", 15), 7));
        ps.add(prod("buckwheat", "Гречка", "🍚", "g", 1, "100g", m("P", 12, "F", 3.4, "C", 64.2, "kcal", 335.4), n("клетчатка", 10, "B1 (тиамин)", .10, "Калий (K)", 460, "Магний (Mg)", 231, "Фосфор (P)", 347, "Марганец (Mn)", 1.3, "Медь (Cu)", .7, "Хром (Cr)", 7), 365));
        ps.add(prod("apple", "Яблоко", "🍎", "g", 1, "100g", m("P", .3, "F", .2, "C", 14, "kcal", 52), n("C", 4.6, "Калий (K)", 107, "клетчатка", 2.4), 20));
        ps.add(prod("omega3", "Омега 3", "🟡", "pcs", 1, "unit", m("F", .71, "kcal", 6.95), n("α-линоленовая", .2, "линолевая", .12), 365));
        return ps;
    }

    static ArrayList<Meal> defaultMeals() {
        ArrayList<Meal> ms = new ArrayList<>();
        ms.add(new Meal("morning", "Утренник"));
        Meal bf = new Meal("breakfast", "Завтрак");
        bf.rows.add(new RowItem("bf_kefir", "kefir", 300, "g"));
        bf.rows.add(new RowItem("bf_flax", "flax", 15, "g"));
        bf.rows.add(new RowItem("bf_banana", "banana", 120, "g"));
        bf.rows.add(new RowItem("bf_water", "water", 500, "g"));
        bf.rows.add(new RowItem("bf_oats", "oats", 120, "g"));
        bf.rows.add(new RowItem("bf_egg", "egg", 2, "pcs"));
        bf.rows.add(new RowItem("bf_cheese", "cheese", 60, "g"));
        bf.rows.add(new RowItem("bf_orange", "orange", 1, "pcs"));
        bf.rows.add(new RowItem("bf_bread", "bread", 2, "pcs"));
        bf.rows.add(new RowItem("bf_d3", "d3", 1, "pcs"));
        bf.rows.add(new RowItem("bf_b12", "b12", 1, "pcs"));
        bf.rows.add(new RowItem("bf_tea", "tea", 500, "g"));
        ms.add(bf);
        Meal lunch = new Meal("lunch", "Обед");
        lunch.rows.add(new RowItem("ln_chicken", "chicken", 100, "g"));
        lunch.rows.add(new RowItem("ln_veg", "veg", 200, "g"));
        lunch.rows.add(new RowItem("ln_spinach", "spinach", 42, "g"));
        lunch.rows.add(new RowItem("ln_seeds", "seeds", 25, "g"));
        lunch.rows.add(new RowItem("ln_oil", "oil", 10, "g"));
        lunch.rows.add(new RowItem("ln_salt", "salt", 4, "g"));
        lunch.rows.add(new RowItem("ln_water", "water", 500, "g"));
        lunch.rows.add(new RowItem("ln_tea", "tea", 500, "g"));
        ms.add(lunch);
        Meal dinner = new Meal("dinner", "Ужин");
        dinner.rows.add(new RowItem("dn_lentils", "lentils", 80, "g"));
        dinner.rows.add(new RowItem("dn_cottage", "cottage", 120, "g"));
        dinner.rows.add(new RowItem("dn_tea", "tea", 500, "g"));
        dinner.rows.add(new RowItem("dn_water", "water", 500, "g"));
        ms.add(dinner);
        return ms;
    }

    static Product prod(String id, String name, String icon, String stockUnit, double unitG, String base, Map<String, Double> macros, Map<String, Double> nutrients, int shelf) {
        Product p = new Product(id, name, icon, stockUnit, unitG, base, shelf);
        p.m.putAll(macros);
        p.n.putAll(nutrients);
        return p;
    }

    static Map<String, Double> m(Object... xs) {
        Map<String, Double> out = new LinkedHashMap<>();
        out.put("P", 0.0); out.put("F", 0.0); out.put("C", 0.0); out.put("kcal", 0.0);
        for (int i = 0; i + 1 < xs.length; i += 2) out.put(String.valueOf(xs[i]), ((Number) xs[i + 1]).doubleValue());
        return out;
    }

    static Map<String, Double> n(Object... xs) {
        Map<String, Double> out = new LinkedHashMap<>();
        for (int i = 0; i + 1 < xs.length; i += 2) out.put(String.valueOf(xs[i]), ((Number) xs[i + 1]).doubleValue());
        return out;
    }

    static ArrayList<Nutrient> nutrients() {
        ArrayList<Nutrient> ns = new ArrayList<>();
        ns.add(new Nutrient("Вода", "Вода", "л", 3));
        ns.add(new Nutrient("гистидин", "Гис", "мг", 980));
        ns.add(new Nutrient("изолейцин", "Изо", "г", 1.35));
        ns.add(new Nutrient("лейцин", "Лей", "г", 2.73));
        ns.add(new Nutrient("лизин", "Лиз", "г", 2.70));
        ns.add(new Nutrient("метионин + цистеин", "Мет+Цис", "г", 1.05));
        ns.add(new Nutrient("фенилаланин + тирозин", "Фен+Тир", "г", 2.34));
        ns.add(new Nutrient("треонин", "Тре", "г", 1.05));
        ns.add(new Nutrient("триптофан", "Трип", "мг", 280));
        ns.add(new Nutrient("валин", "Вал", "г", 1.82));
        ns.add(new Nutrient("линолевая", "Линол.", "г", 10));
        ns.add(new Nutrient("α-линоленовая", "α-линол.", "г", 1.2));
        ns.add(new Nutrient("клетчатка", "Клетч.", "г", 38));
        ns.add(new Nutrient("A", "Вит. A", "мкг", 750));
        ns.add(new Nutrient("C", "Вит. C", "мг", 110));
        ns.add(new Nutrient("D", "Вит. D", "мкг", 15));
        ns.add(new Nutrient("E", "Вит. E", "мг", 13));
        ns.add(new Nutrient("K", "Вит. K", "мкг", 70));
        ns.add(new Nutrient("B1 (тиамин)", "Б1", "мг", 1.1));
        ns.add(new Nutrient("B2 (рибофлавин)", "Б2", "мг", 1.6));
        ns.add(new Nutrient("B3 (ниацин)", "Б3", "мг", 16));
        ns.add(new Nutrient("B5 (пантотеновая кислота)", "Б5", "мг", 5));
        ns.add(new Nutrient("B6", "Б6", "мг", 1.7));
        ns.add(new Nutrient("B7 (биотин)", "Б7", "мкг", 40));
        ns.add(new Nutrient("B9 (фолат)", "Б9", "мкг", 330));
        ns.add(new Nutrient("B12", "Б12", "мкг", 4));
        ns.add(new Nutrient("холин", "Холин", "мг", 500));
        ns.add(new Nutrient("Кальций (Ca)", "Кальц.", "мг", 1000));
        ns.add(new Nutrient("Фосфор (P)", "Фосф.", "мг", 700));
        ns.add(new Nutrient("Магний (Mg)", "Магн.", "мг", 350));
        ns.add(new Nutrient("Натрий (Na)", "Натр.", "мг", 1500));
        ns.add(new Nutrient("Калий (K)", "Калий", "мг", 3500));
        ns.add(new Nutrient("Хлорид (Cl)", "Хлор.", "мг", 2300));
        ns.add(new Nutrient("Железо (Fe)", "Жел.", "мг", 11));
        ns.add(new Nutrient("Цинк (Zn)", "Цинк", "мг", 12));
        ns.add(new Nutrient("Йод (I)", "Йод", "мкг", 150));
        ns.add(new Nutrient("Селен (Se)", "Селен", "мкг", 70));
        ns.add(new Nutrient("Медь (Cu)", "Медь", "мг", 1.6));
        ns.add(new Nutrient("Марганец (Mn)", "Марг.", "мг", 3));
        ns.add(new Nutrient("Молибден (Mo)", "Молиб.", "мкг", 65));
        ns.add(new Nutrient("Хром (Cr)", "Хром", "мкг", 35));
        ns.add(new Nutrient("Фторид (F⁻)", "Фтор", "мг", 3.5));
        return ns;
    }

    static ArrayList<Field> fields() {
        ArrayList<Field> fs = new ArrayList<>();
        fs.add(new Field("m", "P", "Б", "г", "Белки"));
        fs.add(new Field("m", "F", "Ж", "г", "Жиры"));
        fs.add(new Field("m", "C", "У", "г", "Углеводы"));
        fs.add(new Field("m", "kcal", "ккал", "ккал", "Калории"));
        for (Nutrient n : nutrients()) fs.add(new Field("n", n.key, n.shortName, n.unit, n.key));
        return fs;
    }

    static Map<String, AaRatio> aaRatios() {
        LinkedHashMap<String, AaRatio> a = new LinkedHashMap<>();
        a.put("гистидин", new AaRatio("mg", 25));
        a.put("изолейцин", new AaRatio("g", .045));
        a.put("лейцин", new AaRatio("g", .08));
        a.put("лизин", new AaRatio("g", .07));
        a.put("метионин + цистеин", new AaRatio("g", .03));
        a.put("фенилаланин + тирозин", new AaRatio("g", .07));
        a.put("треонин", new AaRatio("g", .035));
        a.put("триптофан", new AaRatio("mg", 12));
        a.put("валин", new AaRatio("g", .045));
        return a;
    }

    static class Nutrient {
        String key, shortName, unit; double target;
        Nutrient(String key, String shortName, String unit, double target) { this.key = key; this.shortName = shortName; this.unit = unit; this.target = target; }
    }

    static class Field {
        String type, key, shortName, unit, label;
        Field(String type, String key, String shortName, String unit, String label) { this.type = type; this.key = key; this.shortName = shortName; this.unit = unit; this.label = label; }
    }

    static class AaRatio { String kind; double ratio; AaRatio(String kind, double ratio) { this.kind = kind; this.ratio = ratio; } }
    static class Qty { double value; String unit; Qty(double value, String unit) { this.value = value; this.unit = unit; } }
    static class Exp { String text; boolean urgent; Exp(String text, boolean urgent) { this.text = text; this.urgent = urgent; } }

    static class Totals {
        Map<String, Double> m = new LinkedHashMap<>();
        Map<String, Double> n = new LinkedHashMap<>();
        Totals() {
            m.put("P", 0.0); m.put("F", 0.0); m.put("C", 0.0); m.put("kcal", 0.0);
            for (Nutrient x : nutrients()) n.put(x.key, 0.0);
        }
    }

    static class DraftProduct {
        String name = "";
        String qty = "";
        String exp = "";
        int fieldIndex = 0;
        Map<String, Double> m = m();
        Map<String, Double> n = n();
        String getFieldValue(Field f) {
            Double v = "m".equals(f.type) ? m.get(f.key) : n.get(f.key);
            return v == null || Math.abs(v) < 1e-12 ? "" : fmt(v);
        }
        void setFieldValue(Field f, String raw) {
            double v = parseNumber(raw == null ? "" : raw);
            if (!Double.isFinite(v)) v = 0;
            if ("m".equals(f.type)) m.put(f.key, v); else if (Math.abs(v) < 1e-12) n.remove(f.key); else n.put(f.key, v);
        }
    }

    static class AppState {
        String day;
        ArrayList<Product> products = new ArrayList<>();
        ArrayList<Meal> meals = new ArrayList<>();
        Map<String, Integer> buyDays = new LinkedHashMap<>();
        Product product(String id) { for (Product p : products) if (p.id.equals(id)) return p; return null; }
        Meal meal(String id) { for (Meal m : meals) if (m.id.equals(id)) return m; return null; }
        JSONObject toJson() {
            JSONObject o = new JSONObject();
            try {
                o.put("day", day);
                JSONArray ps = new JSONArray(); for (Product p : products) ps.put(p.toJson()); o.put("products", ps);
                JSONArray ms = new JSONArray(); for (Meal m : meals) ms.put(m.toJson()); o.put("meals", ms);
                JSONObject bd = new JSONObject(); for (String k : buyDays.keySet()) bd.put(k, buyDays.get(k)); o.put("buyDays", bd);
            } catch (JSONException ignored) {}
            return o;
        }
        static AppState fromJson(JSONObject o) throws JSONException {
            AppState s = new AppState();
            s.day = o.optString("day", todayMoscow().toString());
            JSONArray ps = o.optJSONArray("products");
            if (ps != null) for (int i = 0; i < ps.length(); i++) s.products.add(Product.fromJson(ps.getJSONObject(i)));
            JSONArray ms = o.optJSONArray("meals");
            if (ms != null) for (int i = 0; i < ms.length(); i++) s.meals.add(Meal.fromJson(ms.getJSONObject(i)));
            JSONObject bd = o.optJSONObject("buyDays");
            if (bd != null) { JSONArray names = bd.names(); if (names != null) for (int i = 0; i < names.length(); i++) { String k = names.getString(i); s.buyDays.put(k, bd.optInt(k, 3)); } }
            return s;
        }
    }

    static class Product {
        String id, name, icon, stockUnit, base, image = "", packLabel = "";
        double unitG;
        int shelfDays, packSize = 0;
        String[] pieceForms = null;
        Map<String, Double> m = m();
        Map<String, Double> n = n();
        ArrayList<Batch> batches = new ArrayList<>();
        Product(String id, String name, String icon, String stockUnit, double unitG, String base, int shelfDays) {
            this.id = id; this.name = name; this.icon = icon; this.stockUnit = stockUnit; this.unitG = unitG; this.base = base; this.shelfDays = shelfDays;
        }
        JSONObject toJson() {
            JSONObject o = new JSONObject();
            try {
                o.put("id", id); o.put("name", name); o.put("icon", icon); o.put("stockUnit", stockUnit); o.put("unitG", unitG); o.put("base", base); o.put("shelfDays", shelfDays); o.put("image", image); o.put("packSize", packSize); o.put("packLabel", packLabel);
                if (pieceForms != null) { JSONArray pf = new JSONArray(); for (String f : pieceForms) pf.put(f); o.put("pieceForms", pf); }
                o.put("m", mapJson(m)); o.put("n", mapJson(n));
                JSONArray bs = new JSONArray(); for (Batch b : batches) bs.put(b.toJson()); o.put("batches", bs);
            } catch (JSONException ignored) {}
            return o;
        }
        static Product fromJson(JSONObject o) throws JSONException {
            Product p = new Product(o.getString("id"), o.optString("name"), o.optString("icon", "🍽️"), o.optString("stockUnit", "g"), o.optDouble("unitG", 1), o.optString("base", "100g"), o.optInt("shelfDays", 30));
            p.image = o.optString("image", ""); p.packSize = o.optInt("packSize", 0); p.packLabel = o.optString("packLabel", "");
            JSONArray pf = o.optJSONArray("pieceForms"); if (pf != null && pf.length() >= 3) p.pieceForms = new String[]{pf.optString(0), pf.optString(1), pf.optString(2)};
            p.m = jsonMap(o.optJSONObject("m"), m()); p.n = jsonMap(o.optJSONObject("n"), n());
            JSONArray bs = o.optJSONArray("batches"); if (bs != null) for (int i = 0; i < bs.length(); i++) p.batches.add(Batch.fromJson(bs.getJSONObject(i)));
            return p;
        }
    }

    static class Batch {
        String id, exp; double qty;
        Batch(String id, double qty, String exp) { this.id = id; this.qty = qty; this.exp = exp; }
        JSONObject toJson() { JSONObject o = new JSONObject(); try { o.put("id", id); o.put("qty", qty); o.put("exp", exp); } catch (JSONException ignored) {} return o; }
        static Batch fromJson(JSONObject o) { return new Batch(o.optString("id", id("b")), o.optDouble("qty", 0), o.optString("exp", null)); }
    }

    static class Meal {
        String id, name; ArrayList<RowItem> rows = new ArrayList<>();
        Meal(String id, String name) { this.id = id; this.name = name; }
        JSONObject toJson() { JSONObject o = new JSONObject(); try { o.put("id", id); o.put("name", name); JSONArray rs = new JSONArray(); for (RowItem r : rows) rs.put(r.toJson()); o.put("rows", rs); } catch (JSONException ignored) {} return o; }
        static Meal fromJson(JSONObject o) throws JSONException { Meal m = new Meal(o.getString("id"), o.optString("name")); JSONArray rs = o.optJSONArray("rows"); if (rs != null) for (int i = 0; i < rs.length(); i++) m.rows.add(RowItem.fromJson(rs.getJSONObject(i))); return m; }
    }

    static class RowItem {
        String id, productId, unit;
        double qty;
        boolean eaten = false;
        String consumedProductId = null, consumedUnit = null;
        double consumedQty = 0;
        boolean nextDelete = false;
        double nextQty = 0;
        String nextUnit = null;
        RowItem(String id, String productId, double qty, String unit) { this.id = id; this.productId = productId; this.qty = qty; this.unit = unit; }
        JSONObject toJson() {
            JSONObject o = new JSONObject();
            try { o.put("id", id); o.put("productId", productId); o.put("qty", qty); o.put("unit", unit); o.put("eaten", eaten); o.put("consumedProductId", consumedProductId); o.put("consumedQty", consumedQty); o.put("consumedUnit", consumedUnit); o.put("nextDelete", nextDelete); o.put("nextQty", nextQty); o.put("nextUnit", nextUnit); } catch (JSONException ignored) {}
            return o;
        }
        static RowItem fromJson(JSONObject o) throws JSONException {
            RowItem r = new RowItem(o.getString("id"), o.optString("productId", o.optString("p")), o.optDouble("qty", o.optDouble("q")), o.optString("unit", o.optString("u", "g")));
            r.eaten = o.optBoolean("eaten", false); r.consumedProductId = o.optString("consumedProductId", null); if ("null".equals(r.consumedProductId)) r.consumedProductId = null; r.consumedQty = o.optDouble("consumedQty", 0); r.consumedUnit = o.optString("consumedUnit", null); if ("null".equals(r.consumedUnit)) r.consumedUnit = null; r.nextDelete = o.optBoolean("nextDelete", false); r.nextQty = o.optDouble("nextQty", 0); r.nextUnit = o.optString("nextUnit", null); if ("null".equals(r.nextUnit)) r.nextUnit = null;
            return r;
        }
    }

    static JSONObject mapJson(Map<String, Double> map) throws JSONException {
        JSONObject o = new JSONObject();
        for (String k : map.keySet()) o.put(k, map.get(k));
        return o;
    }

    static Map<String, Double> jsonMap(JSONObject o, Map<String, Double> fallback) throws JSONException {
        Map<String, Double> out = new LinkedHashMap<>();
        if (fallback != null) out.putAll(fallback);
        if (o == null) return out;
        JSONArray names = o.names();
        if (names != null) for (int i = 0; i < names.length(); i++) { String k = names.getString(i); out.put(k, o.optDouble(k, 0)); }
        return out;
    }
}
