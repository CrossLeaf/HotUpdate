import { combineReducers } from 'redux';
import Immutable from 'seamless-immutable'
import { SET_PROGRESS } from "../actions/ActionTypes";

/**
 * 初始化 state
 */
export const INITIAL_STATE = Immutable({
    progress: 0,
})

/**
 * 傳入 state 和 action 更新數據
 * @param state
 * @param action
 * @returns {*}
 */
function counter(state = INITIAL_STATE, action) {
    // debugger
    switch (action.type) {
        case SET_PROGRESS:
            return state.merge({ progress: action.playload })
        default:
            return state;
    }
}

export default combineReducers({
    counter: counter
});