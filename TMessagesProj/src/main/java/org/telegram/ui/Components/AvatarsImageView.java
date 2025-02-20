package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.widget.FrameLayout;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.voip.VoIPService;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.GroupCallUserCell;

import java.util.Random;

public class AvatarsImageView extends FrameLayout {

    DrawingState[] currentStates = new DrawingState[3];
    DrawingState[] animatingStates = new DrawingState[3];
    boolean wasDraw;

    float transitionProgress = 1f;
    ValueAnimator transitionProgressAnimator;
    boolean updateAfterTransition;

    private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint xRefP = new Paint(Paint.ANTI_ALIAS_FLAG);

    Runnable updateDelegate;
    int currentStyle;
    boolean centered;

    public void commitTransition(boolean animated) {
        if (!wasDraw || !animated) {
            transitionProgress = 1f;
            swapStates();
            return;
        }

        DrawingState[] removedStates = new DrawingState[3];
        boolean changed = false;
        for (int i = 0; i < 3; i++) {
            removedStates[i] = currentStates[i];
            if (currentStates[i].id != animatingStates[i].id) {
                changed = true;
            } else {
                currentStates[i].lastSpeakTime = animatingStates[i].lastSpeakTime;
            }
        }
        if (!changed) {
            transitionProgress = 1f;
            return;
        }
        for (int i = 0; i < 3; i++) {
            boolean found = false;
            for (int j = 0; j < 3; j++) {
                if (currentStates[j].id == animatingStates[i].id) {
                    found = true;
                    removedStates[j] = null;
                    if (i == j) {
                        animatingStates[i].animationType = DrawingState.ANIMATION_TYPE_NONE;
                        GroupCallUserCell.AvatarWavesDrawable wavesDrawable = animatingStates[i].wavesDrawable;
                        animatingStates[i].wavesDrawable = currentStates[i].wavesDrawable;
                        currentStates[i].wavesDrawable = wavesDrawable;
                    } else {
                        animatingStates[i].animationType = DrawingState.ANIMATION_TYPE_MOVE;
                        animatingStates[i].moveFromIndex = j;
                    }
                    break;
                }
            }
            if (!found) {
                animatingStates[i].animationType = DrawingState.ANIMATION_TYPE_IN;
            }
        }

        for (int i = 0; i < 3; i++) {
            if (removedStates[i] != null) {
                removedStates[i].animationType = DrawingState.ANIMATION_TYPE_OUT;
            }
        }
        if (transitionProgressAnimator != null) {
            transitionProgressAnimator.cancel();
        }
        transitionProgress = 0;
        transitionProgressAnimator = ValueAnimator.ofFloat(0, 1f);
        transitionProgressAnimator.addUpdateListener(valueAnimator -> {
            transitionProgress = (float) valueAnimator.getAnimatedValue();
            invalidate();
        });
        transitionProgressAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (transitionProgressAnimator != null) {
                    transitionProgress = 1f;
                    swapStates();
                    if (updateAfterTransition) {
                        updateAfterTransition = false;
                        if (updateDelegate != null) {
                            updateDelegate.run();
                        }
                    }
                    invalidate();
                }
                transitionProgressAnimator = null;
            }
        });
        transitionProgressAnimator.setDuration(220);
        transitionProgressAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
        transitionProgressAnimator.start();
        invalidate();
    }

    private void swapStates() {
        for (int i = 0; i < 3; i++) {
            DrawingState state = currentStates[i];
            currentStates[i] = animatingStates[i];
            animatingStates[i] = state;
        }
    }

    public void updateAfterTransitionEnd() {
        updateAfterTransition = true;
    }

    public void setDelegate(Runnable delegate) {
        updateDelegate = delegate;
    }

    public void setStyle(int currentStyle) {
        this.currentStyle = currentStyle;
        invalidate();
    }

    private static class DrawingState {

        public static final int ANIMATION_TYPE_NONE = -1;
        public static final int ANIMATION_TYPE_IN = 0;
        public static final int ANIMATION_TYPE_OUT = 1;
        public static final int ANIMATION_TYPE_MOVE = 2;

        private AvatarDrawable avatarDrawable;
        private GroupCallUserCell.AvatarWavesDrawable wavesDrawable;
        private long lastUpdateTime;
        private long lastSpeakTime;
        private ImageReceiver imageReceiver;
        TLRPC.TL_groupCallParticipant participant;

        private int id;

        private int animationType;
        private int moveFromIndex;
    }

    Random random = new Random();

    public AvatarsImageView(Context context) {
        super(context);
        for (int a = 0; a < 3; a++) {
            currentStates[a] = new DrawingState();
            currentStates[a].imageReceiver = new ImageReceiver(this);
            currentStates[a].imageReceiver.setRoundRadius(AndroidUtilities.dp(12));
            currentStates[a].avatarDrawable = new AvatarDrawable();
            currentStates[a].avatarDrawable.setTextSize(AndroidUtilities.dp(9));

            animatingStates[a] = new DrawingState();
            animatingStates[a].imageReceiver = new ImageReceiver(this);
            animatingStates[a].imageReceiver.setRoundRadius(AndroidUtilities.dp(12));
            animatingStates[a].avatarDrawable = new AvatarDrawable();
            animatingStates[a].avatarDrawable.setTextSize(AndroidUtilities.dp(9));
        }
        setWillNotDraw(false);
        xRefP.setColor(0);
        xRefP.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
    }

    public void setObject(int index, int account, TLObject object) {
        animatingStates[index].id = 0;
        animatingStates[index].participant = null;
        if (object == null) {
            animatingStates[index].imageReceiver.setImageBitmap((Drawable) null);
            invalidate();
            return;
        }
        TLRPC.User currentUser = null;
        TLRPC.Chat currentChat = null;
        animatingStates[index].lastSpeakTime = -1;
        if (object instanceof TLRPC.TL_groupCallParticipant) {
            TLRPC.TL_groupCallParticipant participant = (TLRPC.TL_groupCallParticipant) object;
            animatingStates[index].participant = participant;
            int id = MessageObject.getPeerId(participant.peer);
            if (id > 0) {
                currentUser = MessagesController.getInstance(account).getUser(id);
                animatingStates[index].avatarDrawable.setInfo(currentUser);
            } else {
                currentChat = MessagesController.getInstance(account).getChat(-id);
                animatingStates[index].avatarDrawable.setInfo(currentChat, account);
            }
            if (currentStyle == 4) {
                if (id == AccountInstance.getInstance(account).getUserConfig().getClientUserId()) {
                    animatingStates[index].lastSpeakTime = 0;
                } else {
                    animatingStates[index].lastSpeakTime = participant.active_date;
                }
            } else {
                animatingStates[index].lastSpeakTime = participant.active_date;
            }
            animatingStates[index].id = id;
        } else if (object instanceof TLRPC.User) {
            currentUser = (TLRPC.User) object;
            animatingStates[index].avatarDrawable.setInfo(currentUser);
            animatingStates[index].id = currentUser.id;
        } else {
            currentChat = (TLRPC.Chat) object;
            animatingStates[index].avatarDrawable.setInfo(currentChat, account);
            animatingStates[index].id = -currentChat.id;
        }
        if (currentUser != null) {
            animatingStates[index].imageReceiver.setForUserOrChat(currentUser, animatingStates[index].avatarDrawable);
        } else {
            animatingStates[index].imageReceiver.setForUserOrChat(currentChat, animatingStates[index].avatarDrawable);
        }
        animatingStates[index].imageReceiver.setRoundRadius(AndroidUtilities.dp(currentStyle == 4 ? 16 : 12));
        int size = AndroidUtilities.dp(currentStyle == 4 ? 32 : 24);
        animatingStates[index].imageReceiver.setImageCoords(0, 0, size, size);
        invalidate();
    }

    @SuppressLint("DrawAllocation")
    @Override
    protected void onDraw(Canvas canvas) {
        wasDraw = true;

        int size = AndroidUtilities.dp(currentStyle == 4 ? 32 : 24);
        int toAdd = AndroidUtilities.dp(currentStyle == 4 ? 24 : 20);
        int drawCount = 0;
        for (int i = 0; i < 3; i++) {
            if (currentStates[i].id != 0) {
                drawCount++;
            }
        }
        int ax = centered ? (getMeasuredWidth() - drawCount * toAdd - AndroidUtilities.dp(currentStyle == 4 ? 8 : 4)) / 2 : (currentStyle == 0 ? 0 : AndroidUtilities.dp(10));
        boolean isMuted = VoIPService.getSharedInstance() != null && VoIPService.getSharedInstance().isMicMute();
        if (currentStyle == 4) {
            paint.setColor(Theme.getColor(Theme.key_inappPlayerBackground));
        } else if (currentStyle != 3) {
            paint.setColor(Theme.getColor(isMuted ? Theme.key_returnToCallMutedBackground : Theme.key_returnToCallBackground));
        }

        int animateToDrawCount = 0;
        for (int i = 0; i < 3; i++) {
            if (animatingStates[i].id != 0) {
                animateToDrawCount++;
            }
        }
        boolean useAlphaLayer = currentStyle == 0 || currentStyle == 1 || currentStyle == 3 || currentStyle == 4 || currentStyle == 5;
        if (useAlphaLayer) {
            canvas.saveLayerAlpha(0, 0, getMeasuredWidth(), getMeasuredHeight(), 255, Canvas.ALL_SAVE_FLAG);
        }
        for (int a = 2; a >= 0; a--) {
            for (int k = 0; k < 2; k++) {
                if (k == 0 && transitionProgress == 1f) {
                    continue;
                }
                DrawingState[] states = k == 0 ? animatingStates : currentStates;


                if (k == 1 && transitionProgress != 1f && states[a].animationType != DrawingState.ANIMATION_TYPE_OUT) {
                    continue;
                }
                ImageReceiver imageReceiver = states[a].imageReceiver;
                if (!imageReceiver.hasImageSet()) {
                    continue;
                }
                if (k == 0) {
                    int toAx = centered ? (getMeasuredWidth() - animateToDrawCount * toAdd - AndroidUtilities.dp(currentStyle == 4 ? 8 : 4)) / 2 : AndroidUtilities.dp(10);
                    imageReceiver.setImageX(toAx + toAdd * a);
                } else {
                    imageReceiver.setImageX(ax + toAdd * a);
                }

                if (currentStyle == 0) {
                    imageReceiver.setImageY((getMeasuredHeight() - size) / 2f);
                } else {
                    imageReceiver.setImageY(AndroidUtilities.dp(currentStyle == 4 ? 8 : 6));
                }

                boolean needRestore = false;
                float alpha = 1f;
                if (transitionProgress != 1f) {
                    if (states[a].animationType == DrawingState.ANIMATION_TYPE_OUT) {
                        canvas.save();
                        canvas.scale(1f - transitionProgress, 1f - transitionProgress, imageReceiver.getCenterX(), imageReceiver.getCenterY());
                        needRestore = true;
                        alpha = 1f - transitionProgress;
                    } else if (states[a].animationType == DrawingState.ANIMATION_TYPE_IN) {
                        canvas.save();
                        canvas.scale(transitionProgress, transitionProgress, imageReceiver.getCenterX(), imageReceiver.getCenterY());
                        alpha = transitionProgress;
                        needRestore = true;
                    } else if (states[a].animationType == DrawingState.ANIMATION_TYPE_MOVE) {
                        int toAx = centered ? (getMeasuredWidth() - animateToDrawCount * toAdd - AndroidUtilities.dp(currentStyle == 4 ? 8 : 4)) / 2 : AndroidUtilities.dp(10);
                        int toX = toAx + toAdd * a;
                        int fromX = ax + toAdd * states[a].moveFromIndex;
                        imageReceiver.setImageX((int) (toX * transitionProgress + fromX * (1f - transitionProgress)));
                    } else if (states[a].animationType == DrawingState.ANIMATION_TYPE_NONE && centered) {
                        int toAx = (getMeasuredWidth() - animateToDrawCount * toAdd - AndroidUtilities.dp(currentStyle == 4 ? 8 : 4)) / 2;
                        int toX = toAx + toAdd * a;
                        int fromX = ax + toAdd * a;
                        imageReceiver.setImageX((int) (toX * transitionProgress + fromX * (1f - transitionProgress)));
                    }
                }

                float avatarScale = 1f;
                if (a != states.length - 1) {
                    if (currentStyle == 1 || currentStyle == 3 || currentStyle == 5) {
                        canvas.drawCircle(imageReceiver.getCenterX(), imageReceiver.getCenterY(), AndroidUtilities.dp(13), xRefP);
                        if (states[a].wavesDrawable == null) {
                            if (currentStyle == 5) {
                                states[a].wavesDrawable = new GroupCallUserCell.AvatarWavesDrawable(AndroidUtilities.dp(14), AndroidUtilities.dp(16));
                            } else {
                                states[a].wavesDrawable = new GroupCallUserCell.AvatarWavesDrawable(AndroidUtilities.dp(17), AndroidUtilities.dp(21));
                            }
                        }
                        if (currentStyle == 5) {
                            states[a].wavesDrawable.setColor(ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_voipgroup_speakingText), (int) (255 * 0.3f * alpha)));
                        }
                        if (states[a].participant != null && states[a].participant.amplitude > 0) {
                            states[a].wavesDrawable.setShowWaves(true, this);
                            float amplitude = states[a].participant.amplitude * 15f;
                            states[a].wavesDrawable.setAmplitude(amplitude);
                        } else {
                            states[a].wavesDrawable.setShowWaves(false, this);
                        }
                        if (currentStyle == 5 && (SystemClock.uptimeMillis() - states[a].participant.lastSpeakTime) > 500) {
                            updateDelegate.run();
                        }
                        states[a].wavesDrawable.update();
                        if (currentStyle == 5) {
                            states[a].wavesDrawable.draw(canvas, imageReceiver.getCenterX(), imageReceiver.getCenterY(), this);
                            invalidate();
                        }
                        avatarScale = states[a].wavesDrawable.getAvatarScale();
                    } else if (currentStyle == 4) {
                        canvas.drawCircle(imageReceiver.getCenterX(), imageReceiver.getCenterY(), AndroidUtilities.dp(17), xRefP);
                        if (states[a].wavesDrawable == null) {
                            states[a].wavesDrawable = new GroupCallUserCell.AvatarWavesDrawable(AndroidUtilities.dp(17), AndroidUtilities.dp(21));
                        }
                        states[a].wavesDrawable.setColor(ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_voipgroup_listeningText), (int) (255 * 0.3f * alpha)));
                        long currentTime = System.currentTimeMillis();
                        if (currentTime - states[a].lastUpdateTime > 100) {
                            states[a].lastUpdateTime = currentTime;
                            if (ConnectionsManager.getInstance(UserConfig.selectedAccount).getCurrentTime() - states[a].lastSpeakTime <= 5) {
                                states[a].wavesDrawable.setShowWaves(true, this);
                                states[a].wavesDrawable.setAmplitude(random.nextInt() % 100);
                            } else {
                                states[a].wavesDrawable.setShowWaves(false, this);
                                states[a].wavesDrawable.setAmplitude(0);
                            }
                        }
                        states[a].wavesDrawable.update();
                        states[a].wavesDrawable.draw(canvas, imageReceiver.getCenterX(), imageReceiver.getCenterY(), this);
                        avatarScale = states[a].wavesDrawable.getAvatarScale();
                    } else {
                        if (useAlphaLayer) {
                            canvas.drawCircle(imageReceiver.getCenterX(), imageReceiver.getCenterY(), AndroidUtilities.dp(currentStyle == 4 ? 17 : 13), xRefP);
                        } else {
                            int paintAlpha = paint.getAlpha();
                            if (alpha != 1f) {
                                paint.setAlpha((int) (paintAlpha * alpha));
                            }
                            canvas.drawCircle(imageReceiver.getCenterX(), imageReceiver.getCenterY(), AndroidUtilities.dp(currentStyle == 4 ? 17 : 13), paint);
                            if (alpha != 1f) {
                                paint.setAlpha(paintAlpha);
                            }
                        }
                    }
                }
                imageReceiver.setAlpha(alpha);
                if (avatarScale != 1f) {
                    canvas.save();
                    canvas.scale(avatarScale, avatarScale, imageReceiver.getCenterX(), imageReceiver.getCenterY());
                    imageReceiver.draw(canvas);
                    canvas.restore();
                } else {
                    imageReceiver.draw(canvas);
                }
                if (needRestore) {
                    canvas.restore();
                }
            }
        }
        if (useAlphaLayer) {
            canvas.restore();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        wasDraw = false;
        for (int a = 0; a < 3; a++) {
            currentStates[a].imageReceiver.onDetachedFromWindow();
            animatingStates[a].imageReceiver.onDetachedFromWindow();
        }
        if (currentStyle == 3) {
            Theme.getFragmentContextViewWavesDrawable().setAmplitude(0);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        for (int a = 0; a < 3; a++) {
            currentStates[a].imageReceiver.onAttachedToWindow();
            animatingStates[a].imageReceiver.onAttachedToWindow();
        }
    }

    public void setCentered(boolean centered) {
        this.centered = centered;
    }
}
