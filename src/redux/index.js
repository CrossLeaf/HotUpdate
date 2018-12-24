import { createStore, applyMiddleware, compose } from 'redux';
// import createLogger from 'redux-logger';
import rootReducer from '../redux/reducer/progressReducer';
// import { rootSaga } from '../saga';
// import createSagaMiddleware, { END } from 'redux-saga';
// const configureStore = preloadedState => {
// const sagaMiddleware = createSagaMiddleware();
const store = createStore(
    rootReducer,
    // preloadedState,
    compose(
        // applyMiddleware(createLogger, sagaMiddleware)
    )
)
// sagaMiddleware.run(rootSaga);
// return store;
// }

// const store = configureStore();

export default store;