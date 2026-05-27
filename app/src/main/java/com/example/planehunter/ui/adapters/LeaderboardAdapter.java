package com.example.planehunter.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.planehunter.R;
import com.example.planehunter.model.LeaderboardEntry;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter for displaying a list of leaderboard entries in a RecyclerView.
 * Highlights the top three ranks with distinct colors.
 */
public class LeaderboardAdapter extends RecyclerView.Adapter<LeaderboardAdapter.ViewHolder> {

    /**
     * ViewHolder for leaderboard items.
     */
    static class ViewHolder extends RecyclerView.ViewHolder {
        /** View displaying the user's rank. */
        public TextView textRank;
        /** View displaying the user's name. */
        public TextView textName;
        /** View displaying the user's total XP. */
        public TextView textXp;
        /** View displaying the user's total capture count. */
        public TextView textCaptures;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            textRank = itemView.findViewById(R.id.textRank);
            textName = itemView.findViewById(R.id.textName);
            textXp = itemView.findViewById(R.id.textXp);
            textCaptures = itemView.findViewById(R.id.textCaptures);
        }
    }

    /** The list of leaderboard entries to display. */
    private final List<LeaderboardEntry> entries = new ArrayList<>();

    /**
     * Updates the data set and refreshes the adapter.
     * @param entryList The new list of leaderboard entries.
     */
    public void setItems (List<LeaderboardEntry> entryList){
        entries.clear();
        entries.addAll(entryList);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_leaderboard,parent,false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        LeaderboardEntry entry = entries.get(position);

        holder.textRank.setText(String.valueOf(position + 1));
        holder.textName.setText(entry.name);
        holder.textXp.setText(entry.xp + " XP");
        holder.textCaptures.setText("Captures: " + entry.captures);

        setRankStyle(holder,position);

    }

    /**
     * Sets the visual style (background and text color) for the rank circle based on position.
     * @param holder The ViewHolder.
     * @param position The position in the list.
     */
    private void setRankStyle(ViewHolder holder, int position) {

        int bgColor;
        int textColor;

        switch (position) {
            case 0: // Gold
                bgColor = 0xFFFFD700;
                textColor = 0xFF000000;
                break;

            case 1: // Silver
                bgColor = 0xFFC0C0C0;
                textColor = 0xFF000000;
                break;

            case 2: // Bronze
                bgColor = 0xFFCD7F32;
                textColor = 0xFFFFFFFF;
                break;

            default:
                bgColor = 0xFF4D6BFF;
                textColor = 0xFFFFFFFF;
                break;
        }

        holder.textRank.setBackgroundColor(bgColor);
        holder.textRank.setTextColor(textColor);
    }

    @Override
    public int getItemCount() {
        return entries.size();
    }

}
