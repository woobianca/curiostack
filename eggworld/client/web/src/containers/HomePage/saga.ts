/*
 * MIT License
 *
 * Copyright (c) 2018 Choko (choko@curioswitch.org)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

import { takeLatest } from '@curiostack/base-web';

import { all, call, put, select } from 'redux-saga/effects';

import {
  CheckIngredientsRequest,
  CheckIngredientsResponse,
  FindRecipeRequest,
  FindRecipeResponse,
} from '@curiostack/eggworld-api/curioswitch/eggworld/eggworld-service_pb';
import { EggworldServiceClient } from '@curiostack/eggworld-api/curioswitch/eggworld/eggworld-serviceServiceClientPb';

import { Actions, ActionTypes } from './actions';

import selectHomePage from './selectors';

const client = new EggworldServiceClient('/api', null, null);

function* doCheckIngredients({
  payload: ingredients,
}: ReturnType<typeof Actions.checkIngredients>) {
  const request = new CheckIngredientsRequest();
  request.setSelectedIngredientList(ingredients);
  const response: CheckIngredientsResponse = yield call(
    () =>
      new Promise((resolve, reject) =>
        client.checkIngredients(request, {}, (err, resp) => {
          if (err) {
            reject(err);
          } else {
            resolve(resp);
          }
        }),
      ),
  );
  yield put(Actions.checkIngredientsResponse(response));
}

function* doCook() {
  const state = yield select(selectHomePage);
  const request = new FindRecipeRequest();
  request.setIngredientList(state.eatenFood);
  const response: FindRecipeResponse = yield call(
    () =>
      new Promise((resolve, reject) =>
        client.findRecipe(request, {}, (err, resp) => {
          if (err) {
            reject(err);
          } else {
            resolve(resp);
          }
        }),
      ),
  );
  yield put(Actions.cookResponse(response.getRecipeUrl()));
}

// Individual exports for testing
export default function* rootSaga() {
  yield all([
    takeLatest(ActionTypes.CHECK_INGREDIENTS, doCheckIngredients),
    takeLatest(ActionTypes.COOK, doCook),
  ]);
}
