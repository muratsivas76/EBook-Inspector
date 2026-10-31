package net.murat.uikit;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;

/**
 * MColorPicker is a listener-supported JColorChooser alternative for Android.
 * Optimized with a 48-color grid layout utilizing ultra-compact thumbnail views.
 *
 * @author Murat
 */
public class MColorPicker {
  
  private final Activity hostActivity;
  private int chosenColor = Color.BLACK;
  
  // Expanded 48-color high-density material design matrix palette configuration
  private final int[] colorPalette = new int[] {
    0xFF000000, 0xFF212121, 0xFF424242, 0xFF616161, 0xFF757575, 0xFF9E9E9E,
    0xFFBDBDBD, 0xFFE0E0E0, 0xFFEEEEEE, 0xFFF5F5F5, 0xFFFAFAFA, 0xFFFFFFFF,
    0xFFFFEBEE, 0xFFFFCDD2, 0xFFEF9A9A, 0xFFE57373, 0xFFEF5350, 0xFFF44336,
    0xFFE53935, 0xFFD32F2F, 0xFFC62828, 0xFFB71C1C, 0xFFFF8A80, 0xFFFF5252,
    0xFFE91E63, 0xFF9C27B0, 0xFF673AB7, 0xFF3F51B5, 0xFF2196F3, 0xFF03A9F4,
    0xFF00BCD4, 0xFF009688, 0xFF4CAF50, 0xFF8BC34A, 0xFFCDDC39, 0xFFFFEB3B,
    0xFFFFC107, 0xFFFF9800, 0xFFFF5722, 0xFF795548, 0xFF9E9E9E, 0xFF607D8B,
    0xFF00E676, 0xFF00B0FF, 0xFF2979FF, 0xFF651FFF, 0xFFFF1744, 0xFFFF9100
  };
  
  // Callback communication loop interface to intercept live hex color mutations asynchronously
  public interface OnColorSelectedListener {
    void onColorSelected(int color);
  }
  
  public MColorPicker(Activity hostActivity) {
    if (hostActivity == null) {
      throw new IllegalArgumentException("Host Activity cannot be null");
    }
    this.hostActivity = hostActivity;
  }
  
  // Retained legacy interface endpoint execution pass for strict backwards runtime parity
  public int getColor() {
    buildAndShowGridDialog(null);
    return this.chosenColor;
  }
  
  // Standardized modern structural entry point method passing context hooks down to builders
  public void showColorPickerDialog(OnColorSelectedListener listener) {
    buildAndShowGridDialog(listener);
  }
  
  private void buildAndShowGridDialog(final OnColorSelectedListener listener) {
    final GridView gridView = new GridView(this.hostActivity);
    gridView.setNumColumns(6); // Maintains balanced geometry representation tracking bounds
    gridView.setPadding(16, 16, 16, 16);
    gridView.setVerticalSpacing(12);
    gridView.setHorizontalSpacing(12);
    
    AlertDialog.Builder builder = new AlertDialog.Builder(this.hostActivity);
    builder.setView(gridView);
    builder.setCancelable(false);
    
    final AlertDialog dialog = builder.create();
    gridView.setAdapter(new ColorAdapter(dialog, listener));
    dialog.show();
  }
  
  private class ColorAdapter extends BaseAdapter {
    private final AlertDialog parentDialog;
    private final OnColorSelectedListener listener;
    
    public ColorAdapter(AlertDialog dialog, OnColorSelectedListener listener) {
      this.parentDialog = dialog;
      this.listener = listener;
    }
    
    @Override
    public int getCount() {
      return MColorPicker.this.colorPalette.length;
    }
    
    @Override
    public Object getItem(int position) {
      return MColorPicker.this.colorPalette[position];
    }
    
    @Override
    public long getItemId(int position) {
      return position;
    }
    
    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {
      ImageView imageView;
      if (convertView == null) {
        imageView = new ImageView(MColorPicker.this.hostActivity);
        // Scaled down matrix bounding layout bounds from 120 straight to 60 for safe 48 density grouping allocations
        imageView.setLayoutParams(new GridView.LayoutParams(60, 60));
        imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        } else {
        imageView = (ImageView) convertView;
      }
      
      final int currentColor = MColorPicker.this.colorPalette[position];
      imageView.setBackgroundColor(currentColor);
      
      imageView.setOnClickListener(new View.OnClickListener() {
          @Override
          public void onClick(View v) {
            MColorPicker.this.chosenColor = currentColor;
            if (ColorAdapter.this.listener != null) {
              ColorAdapter.this.listener.onColorSelected(currentColor);
            }
            ColorAdapter.this.parentDialog.dismiss();
          }
      });
      
      return imageView;
    }
  }
  
}
