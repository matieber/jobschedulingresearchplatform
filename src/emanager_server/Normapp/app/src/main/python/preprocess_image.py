import cv2
import numpy as np
from PIL import Image
from scipy import ndimage
from skimage import filters

def scaling_depth(img):
    """
    Escalar las profundidades de la imagen de 0 a 255, considerando el menor valor de profundidad (distinto de cero)
    y el maximo como extremos
    :param img: np_image
    :return:
        np_image
    """
    """ depth(mm) = (pixel_value/255) * 4500 """
    # buscar el valor minimo distinto de 0
    min = 0
    if img[img > 0].size:
        min = np.min(img[img > 0])
    # Porq el maximo es siempre 255?
    max = np.max(img)
    i = img.astype(dtype=np.float64)
    # TOCHECK: Tendria q discernir entre el 0 y el minimo? Estoy llevando los minimos a 0.
    i = np.where(i == 0, 0, (i - min) / (max - min) * 255)
    return i.astype(dtype=np.uint8)


def subtraction_over_distance(depths_images, distance):
    depths_images[depths_images > distance] = 0
    # depths_images - (distance-1)


# def edge_detection(depth_image):
#     mask = canny(depth_image/255.)
#     depth_image[mask==False] = 0
#     depth_image[mask] = 255

# WARNING: Ahora no esta haciendo lo que pensaba
def mask_split_background_foreground(depth_image, simple_threshold=90):
    # Altura de separacion foreground - background
    """ Otsu method: Global Threshold """
    val = filters.threshold_otsu(depth_image)
    val = max(simple_threshold, val)
    mask = depth_image < val
    """ Adaptative threshold: Local Threshold """
    # block_size = 51
    # binary_adaptive = filters.threshold_adaptive(depth_image, block_size, offset=-10)
    # mask = ~binary_adaptive
    mask[depth_image == 0] = False
    return mask


def delete_noise_mask_opening_closing(mask):
    # Remove small white regions
    open_img = ndimage.binary_opening(mask)
    # Remove small black hole
    close_img = ndimage.binary_closing(open_img)
    return close_img


def delete_noise_mask_erosion_propagation(mask):
    eroded = ndimage.binary_erosion(mask)
    reconstruction = ndimage.binary_propagation(eroded, mask=mask)
    return reconstruction


def delete_background(image):
    mask = mask_split_background_foreground(image)
    clean_mask = delete_noise_mask_erosion_propagation(mask)
    # clean_mask = delete_noise_mask_opening_closing(mask)
    image_cow = np.multiply(clean_mask, image)
    return image_cow


# def delete_background_images(images):
#     images_without_background = list()
#     for image in images:
#         images_without_background.append(delete_background(image))
#     return np.asarray(images_without_background)

def substract_empty_image(image_cow, empty_image, threshold=15):
    image_dif = image_cow - empty_image
    image_dif = np.absolute(image_dif)
    im = np.where(image_dif <= threshold, 0, image_cow)
    return im.astype(dtype=np.uint8)


def post_delete_pixels_out_regular_distances(image, max_height=1.8):
    """ Modificar para definir automaticamente valores maximo esperable
     y filtrar posibles objetos entre la camara y la vaca que no fueron filtrados
     Altura maxima vaca = distancia minima desde la camara"""
    # 2.8 altura de la camara
    min_pixel_value = (((2.8 - max_height) * 1000.0) / 4500.0) * 255
    im = np.where(image < min_pixel_value, 0, image)
    return im.astype(dtype=np.uint8)


def delete_background_with_empty_image(image_cow, empty_image, threshold=15, max_height=1.8):
    im = substract_empty_image(image_cow, empty_image, threshold)
    im = delete_background(im)
    post_delete_pixels_out_regular_distances(im, max_height)
    return im.astype(dtype=np.uint8)


def scaling_meanNormalization_Gral(x):
    factor = 255.0 / 2.0
    return (x - factor) / factor


def scaling_depth(img):
    """
    Escalar las profundidades de la imagen de 0 a 255, considerando el menor valor de profundidad (distinto de cero)
    y el maximo como extremos
    :param img: np_image
    :return:
        np_image
    """
    """ depth(mm) = (pixel_value/255) * 4500 """
    # buscar el valor minimo distinto de 0
    min = 0
    if img[img > 0].size:
        min = np.min(img[img > 0])
    # Porq el maximo es siempre 255?
    max = np.max(img)
    i = img.astype(dtype=np.float64)
    # TOCHECK: Tendria q discernir entre el 0 y el minimo? Estoy llevando los minimos a 0.
    i = np.where(i == 0, 0, (i - min) / (max - min) * 255)
    return i.astype(dtype=np.uint8)


def edges(img):
    """
    Resaltar los bordes
    :param img: np_image
    :return:
        np_image
    """
    # im = canny(img / 255.)
    # v = np.median(img)
    # sigma = 0.33
    # # apply automatic Canny edge detection using the computed median
    # lower = int(max(0, (1.0 - sigma) * v))
    # upper = int(min(255, (1.0 + sigma) * v))
    # im = cv2.Canny(img, lower, upper)
    # im = cv2.Canny(img, 10, 200)
    im = cv2.Canny(img, 225, 250)
    return im


def img_DE(img):
    d = scaling_depth(img)
    e = edges(d)
    shape = (d.shape[0], d.shape[1], 1)
    return np.concatenate((d.reshape(shape), e.reshape(shape)), axis=-1)


def preprocess_image_using_empty_image(np_image, empty_image):
    """
        Preprocesamiento de imagen, incluye:
            - resta de la imagen de fondo
            - cortado de la parte central de la imagen
            - generacion de tres canales
            - scaling and mean normalization
        :param np_image:
        :return:
    """
    # From process_depth_image module
    np_image = delete_background_with_empty_image(np_image, empty_image, 10)
    # From transform_img_channels module
    # np_image = np_image[100:300, :] # recorte de imagen
    # np_image = img_DEF(np_image)    # generacion de canales
    np_image = img_DE(np_image)  # generacion de canales
    if (len(np_image.shape) == 2):
        np_image = np.expand_dims(np_image, axis=-1)  # Para cuando se use solo profundidad
    # scaling and mean normalization previo a la entrada a la red
    np_image = scaling_meanNormalization_Gral(np_image.astype(dtype=np.float32))  # , copy=False))
    # np_image = scaling_meanNormalization_Gral(np_image.astype(dtype=np.float64))  # , copy=False))
    return np_image


def preprocess_image(imagefilepath):
    np_empty_image = np.array(Image.open('/sdcard/Download/empty_image.png')).astype(float)
    np_image = np.array(Image.open(imagefilepath)).astype(float)
    #imagefilepath = imagefilepath.rsplit(".", 1)[0]

    images_preprocs = preprocess_image_using_empty_image(np_image, np_empty_image)
    expanded = np.expand_dims(images_preprocs, axis=0)
    data_flat = expanded.flatten()
    np.save(file='/sdcard/Download/my.npy', arr=data_flat)
