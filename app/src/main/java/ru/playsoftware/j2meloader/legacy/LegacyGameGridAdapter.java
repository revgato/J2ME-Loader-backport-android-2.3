/*
 * Copyright 2026 J2ME-Loader contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package ru.playsoftware.j2meloader.legacy;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import ru.playsoftware.j2meloader.R;

/** Grid adapter for the platform-only API 10 launcher. */
public final class LegacyGameGridAdapter extends BaseAdapter {
    private final LayoutInflater inflater;
    private final LegacyGameIconLoader iconLoader = new LegacyGameIconLoader();
    private final int iconSize;
    private List<LegacyAppCatalog.Game> games = Collections.emptyList();

    public LegacyGameGridAdapter(Context context) {
        inflater = LayoutInflater.from(context);
        iconSize = (int) (64 * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    public void setGames(List<LegacyAppCatalog.Game> values) {
        if (values == null || values.isEmpty()) {
            games = Collections.emptyList();
        } else {
            games = new ArrayList<LegacyAppCatalog.Game>(values);
        }
        iconLoader.clear();
        notifyDataSetChanged();
    }

    public void close() {
        iconLoader.close();
    }

    @Override
    public int getCount() {
        return games.size();
    }

    @Override
    public LegacyAppCatalog.Game getItem(int position) {
        return games.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Holder holder;
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.legacy_game_grid_item, parent, false);
            holder = new Holder(convertView);
            convertView.setTag(holder);
        } else {
            holder = (Holder) convertView.getTag();
        }

        final LegacyAppCatalog.Game game = getItem(position);
        final ImageView icon = holder.icon;
        final String path = game.getDirectory().getAbsolutePath();
        icon.setTag(path);
        icon.setImageResource(R.mipmap.ic_launcher);
        holder.name.setText(game.getName());
        convertView.setContentDescription(game.getName());
        iconLoader.load(game, iconSize, new LegacyGameIconLoader.Callback() {
            @Override
            public void onIconReady(final Bitmap bitmap) {
                icon.post(new Runnable() {
                    @Override
                    public void run() {
                        if (path.equals(icon.getTag())) {
                            icon.setImageBitmap(bitmap);
                        }
                    }
                });
            }
        });
        return convertView;
    }

    private static final class Holder {
        final ImageView icon;
        final TextView name;

        Holder(View view) {
            icon = (ImageView) view.findViewById(R.id.game_icon);
            name = (TextView) view.findViewById(R.id.game_name);
        }
    }
}
