package com.nigixmosha.app.ui.studio;

import android.content.Context;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.nigixmosha.app.R;
import com.nigixmosha.app.engine.Casting;
import com.nigixmosha.app.engine.model.Types;
import com.nigixmosha.app.ui.Ui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class VoiceAdapter extends BaseAdapter implements Filterable {
    private static final Set<String> HIDDEN_TAGS = new HashSet<>(Arrays.asList("general", "novel", "news", "conversation", "copilot"));

    static final class Row {
        final String header;
        final Types.VoiceProfile voice;
        final String rawId;

        Row(String header, Types.VoiceProfile voice, String rawId) {
            this.header = header;
            this.voice = voice;
            this.rawId = rawId;
        }

        String id() {
            return voice != null ? voice.id : rawId;
        }

        @Override
        public String toString() {
            if (header != null) return header;
            return voice != null ? label(voice) : rawId;
        }
    }

    private final Context context;
    private final List<Row> rows = new ArrayList<>();

    VoiceAdapter(Context context) {
        this.context = context;
    }

    static String label(Types.VoiceProfile v) {
        String locale = v.langs.isEmpty() || "*".equals(v.langs.get(0)) ? "" : " · " + v.langs.get(0);
        List<String> tags = new ArrayList<>();
        for (String t : v.tags) {
            if (HIDDEN_TAGS.contains(t)) continue;
            tags.add(t);
            if (tags.size() == 2) break;
        }
        return v.name + " · " + v.gender + locale + (tags.isEmpty() ? "" : " · " + String.join(", ", tags));
    }

    void set(List<Casting.Candidate> pool, String currentId, String nativeHeader) {
        rows.clear();
        boolean inPool = false;
        for (Casting.Candidate c : pool) if (c.voice.id.equals(currentId)) inPool = true;
        if (!inPool && currentId != null && !currentId.isEmpty()) rows.add(new Row(null, null, currentId));
        List<Casting.Candidate> nat = new ArrayList<>();
        List<Casting.Candidate> other = new ArrayList<>();
        for (Casting.Candidate c : pool) (c.nativeVoice ? nat : other).add(c);
        if (!nat.isEmpty()) {
            rows.add(new Row(nativeHeader, null, null));
            for (Casting.Candidate c : nat) rows.add(new Row(null, c.voice, null));
        }
        if (!other.isEmpty()) {
            rows.add(new Row(context.getString(R.string.multilingual), null, null));
            for (Casting.Candidate c : other) rows.add(new Row(null, c.voice, null));
        }
        notifyDataSetChanged();
    }

    String labelFor(String id) {
        for (Row r : rows) if (r.header == null && id != null && id.equals(r.id())) return r.toString();
        return id == null ? "" : id;
    }

    @Override
    public int getCount() {
        return rows.size();
    }

    @Override
    public Row getItem(int position) {
        return rows.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public boolean areAllItemsEnabled() {
        return false;
    }

    @Override
    public boolean isEnabled(int position) {
        return rows.get(position).header == null;
    }

    @Override
    public int getViewTypeCount() {
        return 2;
    }

    @Override
    public int getItemViewType(int position) {
        return rows.get(position).header != null ? 0 : 1;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Row row = rows.get(position);
        TextView v = convertView instanceof TextView ? (TextView) convertView : new TextView(context);
        int h = Ui.dpi(context, 16);
        if (row.header != null) {
            v.setPadding(h, Ui.dpi(context, 14), h, Ui.dpi(context, 6));
            v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            v.setTypeface(android.graphics.Typeface.MONOSPACE);
            v.setAllCaps(true);
            v.setLetterSpacing(0.1f);
            v.setTextColor(ContextCompat.getColor(context, R.color.text_tertiary));
        } else {
            v.setPadding(h, Ui.dpi(context, 12), h, Ui.dpi(context, 12));
            v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            v.setTypeface(android.graphics.Typeface.DEFAULT);
            v.setAllCaps(false);
            v.setLetterSpacing(0f);
            v.setTextColor(ContextCompat.getColor(context, R.color.text_primary));
        }
        v.setText(row.toString());
        return v;
    }

    @Override
    public Filter getFilter() {
        return new Filter() {
            @Override
            protected FilterResults performFiltering(CharSequence constraint) {
                FilterResults r = new FilterResults();
                r.values = rows;
                r.count = rows.size();
                return r;
            }

            @Override
            protected void publishResults(CharSequence constraint, FilterResults results) {
                notifyDataSetChanged();
            }

            @Override
            public CharSequence convertResultToString(Object resultValue) {
                return resultValue == null ? "" : resultValue.toString();
            }
        };
    }
}
