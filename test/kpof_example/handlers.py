import kopf


@kopf.on.resume('kopfexamples')
def resume_fn_1(body, **kwargs):
    print(f'RESUMED 1st')
    print(body)


@kopf.on.create('kopfexamples')
def create_fn_1(body, **kwargs):
    print('CREATED 1st')
    print(body)


@kopf.on.resume('kopfexamples')
def resume_fn_2(body, **kwargs):
    print(f'RESUMED 2nd')
    print(body)


@kopf.on.create('kopfexamples')
def create_fn_2(body, **kwargs):
    print('CREATED 2nd')


@kopf.on.update('kopfexamples')
def update_fn(old, new, diff, **kwargs):
    print('UPDATED')
    print(old)
    print(new)
    print(diff)


@kopf.on.delete('kopfexamples')
def delete_fn_1(body, **kwargs):
    print('DELETED 1st')
    print(body)


@kopf.on.delete('kopfexamples')
def delete_fn_2(body, **kwargs):
    print('DELETED 2nd')
    print(body)


@kopf.on.field('kopfexamples', field='spec.field')
def field_fn(old, new, **kwargs):
    print(f'FIELD CHANGED: {old} -> {new}')