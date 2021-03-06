import React from 'react';
import { saveStateVals } from '../../constants/saveStateVals';
import { isVideoPublished, hasVideoExpired } from '../../util/isVideoPublished';
import { hasUnpublishedChanges } from '../../util/hasUnpublishedChanges';
import { getVideoBlock } from '../../util/getVideoBlock';
import { getStore } from '../../util/storeAccessor';

export default class VideoPublishBar extends React.Component {
  videoIsCurrentlyPublishing() {
    return this.props.saveState.publishing === saveStateVals.inprogress;
  }

  videoHasUnpublishedChanges() {
    return hasUnpublishedChanges(
      this.props.video,
      this.props.publishedVideo,
      this.props.editableFields
    );
  }

  isPublishingDisabled() {
    return (
      this.videoIsCurrentlyPublishing() ||
      this.props.videoEditOpen ||
      !this.videoHasUnpublishedChanges() ||
      (this.props.canonicalVideoPageExists() && this.props.requiredComposerFieldsMissing())
    );
  }

  getComposerUrl = () => {
    return getStore().getState().config.composerUrl;
  };

  publishVideo = () => {
    const videoBlock = getVideoBlock(
      this.props.video.id,
      this.props.video.title,
      this.props.video.source
    );

    if (this.props.canonicalVideoPageExists()) {
      this.props.updateVideoPage(
        this.props.video,
        this.getComposerUrl(),
        videoBlock,
        this.props.usages
      );
    }

    this.props.publishVideo();
  };

  renderPublishButtonText() {
    if (this.videoIsCurrentlyPublishing()) {
      return <span>Publishing</span>;
    }

    if (
      isVideoPublished(this.props.publishedVideo) &&
      !this.videoHasUnpublishedChanges()
    ) {
      return <span>Published</span>;
    }

    return <span>Publish</span>;
  }

  renderPublishButton() {
    return (
      <button
        type="button"
        className="btn"
        disabled={this.isPublishingDisabled()}
        onClick={this.publishVideo}
      >
        {this.renderPublishButtonText()}
      </button>
    );
  }

  renderVideoPublishedInfo() {
    if (hasVideoExpired(this.props.publishedVideo)) {
      return <div className="publish__label label__expired">Expired</div>;
    } else if (isVideoPublished(this.props.publishedVideo)) {
      return <div className="publish__label label__live">Live</div>;
    }
    return <div className="publish__label label__draft">Draft</div>;
  }

  render() {
    if (!this.props.video) {
      return false;
    }

    return (
      <div className="flex-container flex-grow publish-bar">
        {this.renderVideoPublishedInfo()}
        <div className="flex-spacer" />
        {this.renderPublishButton()}
      </div>
    );
  }
}
