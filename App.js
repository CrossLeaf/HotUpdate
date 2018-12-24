/**
 * Sample React Native App
 * https://github.com/facebook/react-native
 *
 * @format
 * @flow
 */

import React, { Component } from 'react';
import {
    Alert,
    DeviceEventEmitter,
    NativeModules,
    Platform,
    StyleSheet,
    Text,
    TouchableOpacity,
    View
} from 'react-native';
import UpdateApp from "./UpdateApp.js";
import { Provider } from 'react-redux';
import { createStore, } from 'redux';
import reducer from './src/redux/reducer/progressReducer'

const store = createStore(reducer);

type Props = {};
export default class App extends Component<Props> {
    componentWillMount() {
        // DeviceEventEmitter.addListener('renew_event', (e) => {
        //     Alert.alert(e.renewType, e.data,null,{ cancelable: false })
        // });
    }

    render() {
        return (
            <View>
                <Provider store={store}>
                    <UpdateApp/>
                </Provider>
            </View>
        );
    }
}
const styles = StyleSheet.create({
    container: {
        flex: 1,
        justifyContent: 'center',
        alignItems: 'center',
        backgroundColor: 'red',
    },
    welcome: {
        fontSize: 20,
        textAlign: 'center',
        margin: 10,
    },
    instructions: {
        textAlign: 'center',
        color: '#333333',
        marginBottom: 5,
    },
});
