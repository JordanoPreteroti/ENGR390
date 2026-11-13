package com.coen390.engr390.view;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.coen390.engr390.R;
import com.coen390.engr390.controller.BACController;
import com.coen390.engr390.model.Profile;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Rows of the profiles list: avatar, name, current BAC and status dot. */
public class ProfileAdapter extends RecyclerView.Adapter<ProfileAdapter.Holder> {

    public interface Listener {
        void onProfileClick(Profile profile);
        void onProfileLongClick(Profile profile);
        // region Profiles: edit button callback
        void onProfileEdit(Profile profile);
        // endregion
    }

    private final List<Profile> profiles = new ArrayList<>();
    private final Listener listener;

    public ProfileAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setProfiles(List<Profile> newProfiles) {
        profiles.clear();
        profiles.addAll(newProfiles);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_profile, parent, false);
        return new Holder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        Profile p = profiles.get(position);
        double bac = BACController.currentBac(p.getLastBac(), p.getLastTestTime());

        h.tvAvatar.setText(p.getName().substring(0, 1).toUpperCase(Locale.ROOT));
        h.tvName.setText(p.getName());
        // region Profiles: BAC, status label and dot - "--" with no label or dot before the first test
        boolean tested = p.getLastTestTime() != 0;
        h.vDot.setVisibility(tested ? View.VISIBLE : View.INVISIBLE);
        h.tvStatus.setVisibility(tested ? View.VISIBLE : View.GONE);
        if (tested) {
            h.tvBac.setText(StatusUi.formatBac(bac));
            h.tvStatus.setText(StatusUi.label(BACController.getStatus(bac)));
            h.vDot.getBackground().mutate().setTint(
                    StatusUi.dotColor(h.itemView.getContext(), BACController.getStatus(bac)));
        } else {
            h.tvBac.setText(R.string.no_bac);
        }
        // endregion

        // region Profiles: edit button
        h.btnEdit.setOnClickListener(v -> listener.onProfileEdit(p));
        // endregion

        h.itemView.setOnClickListener(v -> listener.onProfileClick(p));
        h.itemView.setOnLongClickListener(v -> {
            listener.onProfileLongClick(p);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return profiles.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final TextView tvAvatar;
        final TextView tvName;
        final TextView tvBac;
        final View vDot;
        // region Profiles: edit button and status label views
        final View btnEdit;
        final TextView tvStatus;
        // endregion

        Holder(View v) {
            super(v);
            // region Profiles: edit button and status label views
            btnEdit = v.findViewById(R.id.btnEdit);
            tvStatus = v.findViewById(R.id.tvStatus);
            // endregion
            tvAvatar = v.findViewById(R.id.tvAvatar);
            tvName = v.findViewById(R.id.tvName);
            tvBac = v.findViewById(R.id.tvBac);
            vDot = v.findViewById(R.id.vDot);
        }
    }
}
