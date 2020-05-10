export function getSpaceTemplates() {
  return fetch(`/portal/rest/v1/social/spaceTemplates/templates?lang=${eXo.env.portal.language}`, {
    method: 'GET',
    credentials: 'include',
  }).then(resp => {
    if (!resp || !resp.ok) {
      throw new Error('Response code indicates a server error', resp);
    } else {
      return resp.json();
    }
  });
}

export function getSpaceMembers(query, offset, limit, expand, role, spaceId) {
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/social/spaces/${spaceId}/users?role=${role}&q=${query || ''}&offset=${offset || 0}&limit=${limit|| 0}&expand=${expand || ''}&returnSize=true`, {
    method: 'GET',
    credentials: 'include',
  }).then(resp => {
    if (!resp || !resp.ok) {
      throw new Error('Response code indicates a server error', resp);
    } else {
      return resp.json();
    }
  });
}

export function getSpaceById(spaceId) {
  return fetch(`/portal/rest/v1/social/spaces/${spaceId}`, {
    method: 'GET',
    credentials: 'include',
  }).then(resp => {
    if (!resp || !resp.ok) {
      throw new Error('Response code indicates a server error', resp);
    } else {
      return resp.json();
    }
  });
}

export function getSpaces(query, offset, limit, filter) {
  const expand = filter === 'requests' ? 'pending' : limit && 'managers' || '';
  return fetch(`/portal/rest/v1/social/spaces?q=${query || ''}&offset=${offset || 0}&limit=${limit|| 0}&filterType=${filter}&returnSize=true&expand=${expand}`, {
    method: 'GET',
    credentials: 'include',
  }).then(resp => {
    if (!resp || !resp.ok) {
      throw new Error('Response code indicates a server error', resp);
    } else {
      return resp.json();
    }
  });
}

export function removeSpace(spaceId) {
  return fetch(`/portal/rest/v1/social/spaces/${spaceId}`, {
    method: 'DELETE',
    credentials: 'include',
  }).then(resp => {
    if (!resp || !resp.ok) {
      throw new Error('Response code indicates a server error', resp);
    }
  });
}

export function updateSpace(space) {
  return fetch(`/portal/rest/v1/social/spaces/${space.id}`, {
    method: 'PUT',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(space),
  }).then(resp => {
    if (!resp || !resp.ok) {
      return resp.text().then((text) => {
        throw new Error(text);
      });
    } else {
      return resp.json();
    }
  });
}

export function createSpace(space) {
  return fetch('/portal/rest/v1/social/spaces/', {
    method: 'POST',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(space),
  }).then(resp => {
    if (!resp || !resp.ok) {
      return resp.text().then((text) => {
        throw new Error(text);
      });
    } else {
      return resp.json();
    }
  });
}

export function leave(spaceId) {
  return fetch(`/portal/rest/homepage/intranet/spaces/leave/${spaceId}`, {
    method: 'DELETE',
    credentials: 'include',
  }).then(resp => {
    if (!resp || !resp.ok) {
      throw new Error('Response code indicates a server error', resp);
    }
  });
}

export function cancel(spaceId) {
  return fetch(`/portal/rest/homepage/intranet/spaces/cancel/${spaceId}`, {
    method: 'DELETE',
    credentials: 'include',
  }).then(resp => {
    if (!resp || !resp.ok) {
      throw new Error('Response code indicates a server error', resp);
    }
  });
}

export function join(spaceId) {
  return fetch(`/portal/rest/homepage/intranet/spaces/join/${spaceId}`, {
    method: 'GET',
    credentials: 'include',
  }).then(resp => {
    if (!resp || !resp.ok) {
      throw new Error('Response code indicates a server error', resp);
    }
  });
}

export function requestJoin(spaceId) {
  return fetch(`/portal/rest/homepage/intranet/spaces/request/${spaceId}`, {
    method: 'GET',
    credentials: 'include',
  }).then(resp => {
    if (!resp || !resp.ok) {
      throw new Error('Response code indicates a server error', resp);
    }
  });
}

export function accept(spaceId) {
  return fetch(`/portal/rest/homepage/intranet/spaces/accept/${spaceId}`, {
    method: 'GET',
    credentials: 'include',
  }).then(resp => {
    if (!resp || !resp.ok) {
      throw new Error('Response code indicates a server error', resp);
    }
  });
}

export function deny(spaceId) {
  return fetch(`/portal/rest/homepage/intranet/spaces/deny/${spaceId}`, {
    method: 'GET',
    credentials: 'include',
  }).then(resp => {
    if (!resp || !resp.ok) {
      throw new Error('Response code indicates a server error', resp);
    }
  });
}

export function acceptUserRequest(spaceDisplayName, userId) {
  return fetch('/portal/rest/v1/social/spacesMemberships', {
    method: 'POST',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      space: spaceDisplayName,
      user: userId,
      role: 'MEMBER',
      status: 'APPROVED',
    }),
  }).then(resp => {
    if (!resp || !resp.ok) {
      throw new Error('Response code indicates a server error', resp);
    }
  });
}

export function refuseUserRequest(spaceDisplayName, userId) {
  return fetch('/portal/rest/v1/social/spacesMemberships', {
    method: 'POST',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      space: spaceDisplayName,
      user: userId,
      status: 'IGNORED',
    }),
  }).then(resp => {
    if (!resp || !resp.ok) {
      throw new Error('Response code indicates a server error', resp);
    }
  });
}

export function cancelInvitation(spaceDisplayName, userId) {
  return fetch('/portal/rest/v1/social/spacesMemberships', {
    method: 'POST',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      space: spaceDisplayName,
      user: userId,
      status: 'IGNORED',
    }),
  }).then(resp => {
    if (!resp || !resp.ok) {
      throw new Error('Response code indicates a server error', resp);
    }
  });
}

export function promoteManager(spaceDisplayName, userId) {
  return fetch('/portal/rest/v1/social/spacesMemberships', {
    method: 'POST',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      space: spaceDisplayName,
      user: userId,
      role: 'manager',
    }),
  }).then(resp => {
    if (!resp || !resp.ok) {
      throw new Error('Response code indicates a server error', resp);
    }
  });
}

export function removeManager(spacePrettyName, username) {
  const id = `${spacePrettyName}:${username}:manager`;
  return fetch(`/portal/rest/v1/social/spacesMemberships/${id}`, {
    method: 'DELETE',
    credentials: 'include',
  }).then(resp => {
    if (!resp || !resp.ok) {
      throw new Error('Response code indicates a server error', resp);
    }
  });
}

export function removeMember(spacePrettyName, username) {
  const id = `${spacePrettyName}:${username}:member`;
  return fetch(`/portal/rest/v1/social/spacesMemberships/${id}`, {
    method: 'DELETE',
    credentials: 'include',
  }).then(resp => {
    if (!resp || !resp.ok) {
      throw new Error('Response code indicates a server error', resp);
    }
  });
}

export function getSuggestionsSpace(){
  return fetch('/portal/rest/homepage/intranet/spaces/suggestions', {
    credentials: 'include'
  }).then(resp => {
    if (!resp || !resp.ok) {
      return resp.text().then((text) => {
        throw new Error(text);
      });
    } else {
      return resp.json();
    }
  });
}

export function ignoreSuggestion(item) {
  const data = {'user': item.username,'space': item.displayName, 'status':'IGNORED'};
  return fetch('/portal/rest/v1/social/spacesMemberships/', {
    method: 'POST',
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(data),
  }).then(resp => {
    if (!resp || !resp.ok) {
      return resp.text().then((text) => {
        throw new Error(text);
      });
    } else {
      return resp.json();
    }
  });
}
