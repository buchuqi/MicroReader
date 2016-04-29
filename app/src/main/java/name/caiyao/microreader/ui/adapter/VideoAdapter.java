package name.caiyao.microreader.ui.adapter;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.support.v7.widget.CardView;
import android.support.v7.widget.PopupMenu;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;

import java.io.IOException;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import butterknife.BindView;
import butterknife.ButterKnife;
import name.caiyao.microreader.R;
import name.caiyao.microreader.api.util.UtilRequest;
import name.caiyao.microreader.bean.weiboVideo.WeiboVideoBlog;
import name.caiyao.microreader.config.Config;
import name.caiyao.microreader.ui.activity.VideoActivity;
import name.caiyao.microreader.ui.activity.VideoWebViewActivity;
import name.caiyao.microreader.utils.DBUtils;
import name.caiyao.microreader.utils.SharePreferenceUtil;
import okhttp3.ResponseBody;
import rx.Observer;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

/**
 * Created by 蔡小木 on 2016/4/29 0029.
 */
public   class VideoAdapter extends RecyclerView.Adapter<VideoAdapter.VideoViewHolder> {

    private ArrayList<WeiboVideoBlog> gankVideoItems;
    private Context mContext;

    public VideoAdapter(Context context,ArrayList<WeiboVideoBlog> gankVideoItems) {
        this.gankVideoItems = gankVideoItems;
        this.mContext = context;
    }

    @Override
    public VideoViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        return new VideoViewHolder(LayoutInflater.from(mContext).inflate(R.layout.video_item, parent, false));
    }

    @Override
    public void onBindViewHolder(final VideoViewHolder holder, int position) {
        final WeiboVideoBlog weiboVideoBlog = gankVideoItems.get(position);
        final String title = weiboVideoBlog.getBlog().getText().replaceAll("&[a-zA-Z]{1,10};", "").replaceAll(
                "<[^>]*>", "");
        if (DBUtils.getDB(mContext).isRead(Config.VIDEO, weiboVideoBlog.getBlog().getPageInfo().getVideoUrl(), 1))
            holder.tvTitle.setTextColor(Color.GRAY);
        else
            holder.tvTitle.setTextColor(Color.BLACK);
        Glide.with(mContext).load(weiboVideoBlog.getBlog().getPageInfo().getVideoPic()).into(holder.mIvVideo);
        holder.tvTitle.setText(title);
        holder.tvTime.setText(weiboVideoBlog.getBlog().getCreateTime());
        holder.btnVideo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                PopupMenu popupMenu = new PopupMenu(mContext, holder.btnVideo);
                popupMenu.getMenuInflater().inflate(R.menu.pop_menu, popupMenu.getMenu());
                popupMenu.getMenu().removeItem(R.id.pop_fav);
                final boolean isRead = DBUtils.getDB(mContext).isRead(Config.VIDEO, weiboVideoBlog.getBlog().getPageInfo().getVideoUrl(), 1);
                if (!isRead)
                    popupMenu.getMenu().findItem(R.id.pop_unread).setTitle("标记为已读");
                else
                    popupMenu.getMenu().findItem(R.id.pop_unread).setTitle("标记为未读");
                popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem item) {
                        switch (item.getItemId()) {
                            case R.id.pop_unread:
                                if (isRead) {
                                    DBUtils.getDB(mContext).insertHasRead(Config.VIDEO, weiboVideoBlog.getBlog().getPageInfo().getVideoUrl(), 0);
                                    holder.tvTitle.setTextColor(Color.BLACK);
                                } else {
                                    DBUtils.getDB(mContext).insertHasRead(Config.VIDEO, weiboVideoBlog.getBlog().getPageInfo().getVideoUrl(), 1);
                                    holder.tvTitle.setTextColor(Color.GRAY);
                                }
                                break;
                            case R.id.pop_share:
                                Intent shareIntent = new Intent();
                                shareIntent.setAction(Intent.ACTION_SEND);
                                shareIntent.putExtra(Intent.EXTRA_TEXT, title + " " + weiboVideoBlog.getBlog().getPageInfo().getVideoUrl() + mContext.getString(R.string.share_tail));
                                shareIntent.setType("text/plain");
                                //设置分享列表的标题，并且每次都显示分享列表
                                mContext.startActivity(Intent.createChooser(shareIntent, mContext.getString(R.string.share)));
                                break;
                        }
                        return true;
                    }
                });
                popupMenu.show();
            }
        });
        holder.cvVideo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                DBUtils.getDB(mContext).insertHasRead(Config.VIDEO, weiboVideoBlog.getBlog().getPageInfo().getVideoUrl(), 1);
                holder.tvTitle.setTextColor(Color.GRAY);
                VideoAdapter.this.getPlayUrl(weiboVideoBlog, title);
            }
        });
    }

    private void getPlayUrl(final WeiboVideoBlog weiboVideoBlog, final String title) {
        final ProgressDialog progressDialog = new ProgressDialog(mContext);
        progressDialog.setMessage(mContext.getString(R.string.fragment_video_get_url));
        progressDialog.show();
        UtilRequest.getUtilApi().getVideoUrl(weiboVideoBlog.getBlog().getPageInfo().getVideoUrl())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<ResponseBody>() {
                    @Override
                    public void onCompleted() {

                    }

                    @Override
                    public void onError(Throwable e) {
                        if (progressDialog.isShowing()) {
                            progressDialog.dismiss();
                            Toast.makeText(mContext, "视频解析失败！", Toast.LENGTH_SHORT).show();
                            mContext.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(weiboVideoBlog.getBlog().getPageInfo().getVideoUrl())));
                        }
                    }

                    @Override
                    public void onNext(ResponseBody responseBody) {
                        //防止停止后继续执行
                        if (progressDialog.isShowing()) {
                            progressDialog.dismiss();
                            try {
                                String shareUrl;
                                Pattern pattern = Pattern.compile("href\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\"'>\\s]+)).*target=\"blank\">http");
                                final Matcher matcher = pattern.matcher(responseBody.string());
                                shareUrl = weiboVideoBlog.getBlog().getPageInfo().getVideoUrl();
                                if (TextUtils.isEmpty(shareUrl)) {
                                    Toast.makeText(mContext, "播放地址为空", Toast.LENGTH_SHORT).show();
                                    return;
                                }
                                if (matcher.find() && matcher.group(1).endsWith(".mp4")) {
                                    mContext.startActivity(new Intent(mContext, VideoActivity.class)
                                            .putExtra("url", matcher.group(1))
                                            .putExtra("shareUrl", shareUrl)
                                            .putExtra("title", title));
                                } else {
                                    if (SharePreferenceUtil.isUseLocalBrowser(mContext))
                                        mContext.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(matcher.group(1))));
                                    else
                                        mContext.startActivity(new Intent(mContext, VideoWebViewActivity.class)
                                                .putExtra("url", matcher.group(1)));
                                }
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                });
    }

    @Override
    public int getItemCount() {
        return gankVideoItems.size();
    }

    public class VideoViewHolder extends RecyclerView.ViewHolder {

        @BindView(R.id.tv_title)
        TextView tvTitle;
        @BindView(R.id.tv_time)
        TextView tvTime;
        @BindView(R.id.cv_video)
        CardView cvVideo;
        @BindView(R.id.iv_video)
        ImageView mIvVideo;
        @BindView(R.id.btn_video)
        Button btnVideo;

        public VideoViewHolder(View itemView) {
            super(itemView);
            ButterKnife.bind(this, itemView);
        }
    }
}
