package com.chienpm.zecorder.ui.adapters;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.chienpm.zecorder.R;
import com.chienpm.zecorder.controllers.settings.VideoSetting;
import com.chienpm.zecorder.data.entities.Video;
import com.chienpm.zecorder.ui.activities.SyncActivity;

import java.util.ArrayList;

public class SyncVideoAdapter extends ArrayAdapter<Video> {
    private static final String TAG = "chienpm_log";
    private boolean mLocalLoaded = false;
    private boolean mDriveLoaded = false;

    public static class ViewHolder {
        public TextView title;
        public TextView resolution;
        public TextView duration;
        public TextView size;
        public ImageView thumb;
        public ImageView sync;
        public ProgressBar progress;
    }

    private final SyncActivity mActivity;
    private final ArrayList<Video> mVideos;
    private ArrayList<Video> mLocalVideos;
    private ArrayList<Video> mDriveVideos;

    private ArrayList<Integer> mProgressValues;

    public SyncVideoAdapter(SyncActivity syncActivity, ArrayList<Video> videos) {
        super(syncActivity, 0, videos);
        mActivity = syncActivity;
        mVideos = videos;
        mProgressValues = new ArrayList<>();
        mLocalVideos = new ArrayList<>();
        mDriveVideos = new ArrayList<>();
    }

    public void setDriveVideos(ArrayList<Video> driveVideos) {
        mDriveVideos = driveVideos;
        mDriveLoaded = true;
        if(mLocalLoaded && mDriveLoaded){
            joinVideos();
        }
    }

    public void setLocalVideos(ArrayList<Video> localVideos){
        mLocalLoaded = true;
        mLocalVideos = localVideos;
        if(mLocalLoaded && mDriveLoaded){
            joinVideos();
        }
    }

    private void joinVideos() {
        mLocalLoaded = false;
        mDriveLoaded = false;

        mVideos.clear();

        // If there is a valid list of {@link Earthquake}s, then add them to the adapter's
        // data set. This will trigger the ListView to notifySettingChanged.
        if(mLocalVideos != null && mDriveVideos!=null){
            int i,j;
            for(Video v :mLocalVideos){
                for(j = 0; j < mDriveVideos.size(); j++){
                    if (TextUtils.equals(v.getTitle(), mDriveVideos.get(j).getTitle()))
                        break;
                }
                if (j==mDriveVideos.size())
                    mVideos.add(v);
            }


            for(Video v: mDriveVideos){
                for(j = 0; j < mLocalVideos.size(); j++){
                    if (TextUtils.equals(v.getTitle(), mLocalVideos.get(j).getTitle()))
                        break;
                }
                if (j==mLocalVideos.size())
                    mVideos.add(v);
            }
        }

        notifyDataSetChanged();

        mActivity.updateUI();
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        View view = convertView;
        final ViewHolder holder;
        if(view == null){
            holder = new ViewHolder();
            view = LayoutInflater.from(getContext()).inflate(R.layout.sync_video_item,  null);
            initViewHolder(view, holder);
            holder.sync.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    int position = (int)holder.sync.getTag();
                    Video video = getItem(position);
                    if(video.isLocalVideo()){
                        //upload to cloud
                        mActivity.uploadVideo(video, holder);
                    }
                    else{
                        //download
                        mActivity.downloadVideo(video, holder);
                    }
                    v.setEnabled(false);
                }
            });

        }
        else {
            holder = (ViewHolder) view.getTag();
        }

        holder.sync.setTag(position);

        final Video video = getItem(position);

        if(video!=null){

            String thumbnailLink = video.getThumbnailLink();

            Glide.with(getContext())
                    .load(thumbnailLink) // or URI/path
                    .into(holder.thumb); //imageview to set thumbnail to

            holder.title.setText(video.getTitle());

            String resolution = VideoSetting.getShortResolution(video.getWidth(), video.getHeight());
            holder.resolution.setText(resolution);

            String size = VideoSetting.getFormattedSize(video.getSize());
            holder.size.setText(size);

            String duration = VideoSetting.getFormattedDuration(video.getDuration());
            holder.duration.setText(duration);

            if(video.isLocalVideo()){
                //to upload
                holder.sync.setImageDrawable(getContext().getDrawable(R.drawable.ic_upload));
            }
            else{//on drive
                //need download
                holder.sync.setImageDrawable(getContext().getDrawable(R.drawable.ic_download));
            }
        }


        return view;
    }

    private void initViewHolder(View viewVideoItem, ViewHolder holder) {
            holder.title = viewVideoItem.findViewById(R.id.sync_tvTitle);
            holder.duration = viewVideoItem.findViewById(R.id.sync_tvDuration);
            holder.size = viewVideoItem.findViewById(R.id.sync_tvSize);
            holder.resolution = viewVideoItem.findViewById(R.id.sync_tvResolution);
            holder.thumb = viewVideoItem.findViewById(R.id.sync_imgThumbnail);
            holder.sync = viewVideoItem.findViewById(R.id.sync_imgSync);
            holder.progress = viewVideoItem.findViewById(R.id.sync_progressBar);

            viewVideoItem.setTag(holder);
            viewVideoItem.setTag(R.id.sync_tvTitle, holder.title);
            viewVideoItem.setTag(R.id.sync_tvDuration, holder.duration);
            viewVideoItem.setTag(R.id.sync_tvSize, holder.size);
            viewVideoItem.setTag(R.id.sync_tvResolution, holder.resolution);
            viewVideoItem.setTag(R.id.sync_imgThumbnail, holder.thumb);
            viewVideoItem.setTag(R.id.sync_imgSync, holder.sync);
            viewVideoItem.setTag(R.id.sync_progressBar, holder.progress);
    }


}