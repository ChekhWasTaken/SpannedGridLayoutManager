package com.chekh.spannedgridlayoutmanagerexample;

import android.graphics.Color;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.chekh.spannedgridlayoutmanager.SpannedGridLayoutManager;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        SpannedGridLayoutManager spannedGridLayoutManager = new SpannedGridLayoutManager(SpannedGridLayoutManager.Orientation.VERTICAL, 3);

        RecyclerView recyclerView = findViewById(R.id.list_items);
        recyclerView.setLayoutManager(spannedGridLayoutManager);
        recyclerView.setHasFixedSize(true);
        recyclerView.setAdapter(new ItemsAdapter());
    }

    private static final class ItemsAdapter extends RecyclerView.Adapter<ItemViewHolder> {
        private final int[] colors = {Color.RED, Color.GREEN, Color.BLUE, Color.CYAN, Color.DKGRAY, Color.MAGENTA, Color.YELLOW};

        private final boolean[] clickedItems = new boolean[100];

        @NonNull
        @Override
        public ItemViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.list_item, parent, false);
            return new ItemViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull final ItemViewHolder holder, final int position) {
            int width = clickedItems[position] ? 2 : 1;
            int height = clickedItems[position] ? 2 : 1;

            SpannedGridLayoutManager.SpanSize spanSize = new SpannedGridLayoutManager.SpanSize(width, height);
            holder.itemView.setLayoutParams(new SpannedGridLayoutManager.SpanLayoutParams(spanSize));
            holder.itemView.setBackgroundColor(colors[position % colors.length]);

            holder.itemView.setOnClickListener(v -> {
                int adapterPosition = holder.getAdapterPosition();

                clickedItems[adapterPosition] = !clickedItems[adapterPosition];
                notifyItemChanged(adapterPosition);
            });

            ((TextView) holder.itemView).setText(position + "");
        }

        @Override
        public int getItemCount() {
            return 100;
        }
    }

    private static final class ItemViewHolder extends RecyclerView.ViewHolder {
        ItemViewHolder(View itemView) {
            super(itemView);
        }
    }
}
