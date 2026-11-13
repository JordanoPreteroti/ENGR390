package com.coen390.engr390.view;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.coen390.engr390.R;
import com.coen390.engr390.model.Profile;

import java.util.ArrayList;
import java.util.List;

/** Rows of the leaderboard: rank, name, drinks and points. */
public class LeaderboardAdapter extends RecyclerView.Adapter<LeaderboardAdapter.Holder> {

    private final List<Profile> profiles = new ArrayList<>();

    public void setProfiles(List<Profile> newProfiles) {
        profiles.clear();
        profiles.addAll(newProfiles);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_leaderboard, parent, false);
        return new Holder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        Profile p = profiles.get(position);
        h.tvRank.setText(String.valueOf(position + 1));
        h.tvName.setText(p.getName());
        h.tvDrinks.setText(p.getDrinks() + (p.getDrinks() == 1 ? " drink" : " drinks"));
        h.tvPoints.setText(p.getPoints() + " pts");
    }

    @Override
    public int getItemCount() {
        return profiles.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final TextView tvRank;
        final TextView tvName;
        final TextView tvDrinks;
        final TextView tvPoints;

        Holder(View v) {
            super(v);
            tvRank = v.findViewById(R.id.tvRank);
            tvName = v.findViewById(R.id.tvName);
            tvDrinks = v.findViewById(R.id.tvDrinks);
            tvPoints = v.findViewById(R.id.tvPoints);
        }
    }
}
