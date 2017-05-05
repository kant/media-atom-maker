import reqwest from 'reqwest';
import { reEstablishSession } from 'babel?presets[]=es2015!panda-session';
import { getStore } from '../util/storeAccessor';

function poll(reqwestBody, timeout) {
  const endTime = Number(new Date()) + timeout;
  const interval = 100;

  function makeRequest(resolve, reject) {
    reqwest(reqwestBody).then(response => resolve(response)).fail(err => {
      if (err.status === 419) {
        const store = getStore();
        const reauthUrl = store.getState().config.reauthUrl;

        reEstablishSession(reauthUrl, 5000).then(
          () => {
            setTimeout(makeRequest, interval, resolve, reject);
          },
          error => {
            throw error;
          }
        );
      } else {
        if (Number(new Date()) < endTime) {
          setTimeout(makeRequest, interval, resolve, reject);
        } else {
          reject(err);
        }
      }
    });
  }

  return new Promise(makeRequest);
}

// when `timeout` > 0, the request will be retried every 100ms until success or timeout
export function pandaReqwest(reqwestBody, timeout = 0) {
  const defaultPayload = {
    contentType: 'application/json',
    method: 'get'
  };

  const payload = !reqwestBody.data
    ? Object.assign(defaultPayload, reqwestBody)
    : Object.assign(defaultPayload, reqwestBody, {
        data: JSON.stringify(reqwestBody.data)
      });

  return new Promise((resolve, reject) => {
    poll(payload, timeout)
      .then(response => resolve(response))
      .catch(error => reject(error));
  });
}
