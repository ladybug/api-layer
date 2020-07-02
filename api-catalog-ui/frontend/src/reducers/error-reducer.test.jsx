/* eslint-disable no-undef */
import {ApiError, CLEAR_ALL_ERRORS, MessageType, SEND_ERROR} from '../constants/error-constants';
import errorReducer from './error-reducer';

describe('>>> Error reducer tests', () => {
    it('should return default state in the default action', () => {
        expect(errorReducer({errors: ["bar", "foo"]}, {})).toEqual({errors: ["bar", "foo"]})
    });

    it('should handle CLEAR_ALL_ERRORS', () => {
        const expectedState = {
            errors: [],
        };

        expect(errorReducer({ errors: ["bar", "foo"] }, { type: CLEAR_ALL_ERRORS, payload: null })).toEqual(expectedState);
    });

    it('should handle SEND_ERROR', () => {
        const error = new ApiError('ABC123', 123, new MessageType(40, 'ERROR', 'E'), 'Bad stuff happened');
        const expectedState = {
            errors: [error],
        };

        expect(errorReducer({ errors: [] }, { type: SEND_ERROR, payload: error })).toEqual(expectedState);
    });
});
