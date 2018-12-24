import React, { Component } from 'react';
import { DeviceEventEmitter, NativeModules, Text, View } from 'react-native';
import { setProgressStatus } from "./src/redux/actions/progressAction";
import { connect } from 'react-redux'
class UpdateApp extends Component {

    constructor(props) {
        super(props);
        this.state = {
            progress: "0",
        }
    }

    componentWillMount() {
        //监听事件名为EventName的事件
        DeviceEventEmitter.addListener('ProgressEvent', (e) => {
            console.log(e.progress)
            this.props.setProgress(e.progress)
        });
        // NativeModules.NativeVersion.updateVersion();

    }

    componentDidMount() {

    }

    render() {
        return (
            <View>
                <Text>reload 了：{this.props.progress}%</Text>
            </View>
        );
    }

    setProgress(progress) {
        this.setState({
            progress: progress
        })
    }
}

const mapStateToProps = (state) => {
    return {
        progress: state.counter.progress,
    }
}
/**
 * 註冊 dispatch 讓此頁可以傳送 action
 * @param dispatch
 */
const mapStateDispatch = (dispatch) => {
    return {
        setProgress: (progress) => dispatch(setProgressStatus(progress))
    }
}

export default connect()(UpdateApp)