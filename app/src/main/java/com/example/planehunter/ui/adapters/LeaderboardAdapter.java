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

public class LeaderboardAdapter extends RecyclerView.Adapter<LeaderboardAdapter.ViewHolder> {

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView textRank;
        TextView textName;
        TextView textXp;
        TextView textCaptures;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            textRank = itemView.findViewById(R.id.textRank);
            textName = itemView.findViewById(R.id.textName);
            textXp = itemView.findViewById(R.id.textXp);
            textCaptures = itemView.findViewById(R.id.textCaptures);
        }
    }

    private final List<LeaderboardEntry> entries = new ArrayList<>();

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
        holder.textName.setText(String.valueOf(position+1));//because starts in 0
        holder.textName.setText(entry.name);
        holder.textXp.setText("XP: " + entry.xp);
        holder.textCaptures.setText("Captures: " + entry.captures);

    }

    @Override
    public int getItemCount() {
        return entries.size();
    }

}
