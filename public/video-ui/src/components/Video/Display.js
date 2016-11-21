import React from 'react';
import VideoEdit from '../VideoEdit/VideoEdit';
import VideoAssets from '../VideoAssets/VideoAssets';
import VideoPublishButton from '../VideoPublishButton/VideoPublishButton';
import VideoPreview from '../VideoPreview/VideoPreview';
import VideoUsages from '../VideoUsages/VideoUsages';
import SaveButton from '../utils/SaveButton';

class VideoDisplay extends React.Component {

  state = {
    editable: false
  };

  componentWillMount() {
    this.props.videoActions.getVideo(this.props.params.id);
  }

  saveVideo = () => {
    this.props.videoActions.saveVideo(this.props.video);
    this.setState({
      editable: false
    })
  };

  updateVideo = (video) => {
    this.props.videoActions.updateVideo(video);
  };

  resetVideo = () => {
    this.props.videoActions.getVideo(this.props.video.id);
  };

  publishVideo = () => {
    this.props.videoActions.publishVideo(this.props.video.id);
    this.setState({
      editable: false
    });
  };

  enableEditing = () => {
    this.setState({
      editable: true
    });
  };

  render() {
    const video = this.props.video && this.props.params.id === this.props.video.id ? this.props.video : undefined;

    if (!video) {
      return <div className="container">Loading... </div>;
    }

    return (
        <div className="video">

          <div className="video__sidebar video-details">
            <form className="form video__sidebar__group">
              <VideoEdit video={this.props.video || {}} updateVideo={this.updateVideo} saveVideo={this.saveVideo} resetVideo={this.resetVideo} />
              <VideoPublishButton video={this.props.video || {}} publishVideo={this.publishVideo} />
            </form>
          </div>

          <div className="video__main">
            <div className="video__main__header">
              <VideoPreview video={this.props.video || {}} />
              <VideoAssets video={this.props.video || {}} />
            </div>
            <VideoUsages video={this.props.video} />
          </div>
        </div>
    )
  }
}

//REDUX CONNECTIONS
import { connect } from 'react-redux';
import { bindActionCreators } from 'redux';
import * as getVideo from '../../actions/VideoActions/getVideo';
import * as saveVideo from '../../actions/VideoActions/saveVideo';
import * as updateVideo from '../../actions/VideoActions/updateVideo';
import * as publishVideo from '../../actions/VideoActions/publishVideo';

function mapStateToProps(state) {
  return {
    video: state.video
  };
}

function mapDispatchToProps(dispatch) {
  return {
    videoActions: bindActionCreators(Object.assign({}, getVideo, saveVideo, updateVideo, publishVideo), dispatch)
  };
}

export default connect(mapStateToProps, mapDispatchToProps)(VideoDisplay);
